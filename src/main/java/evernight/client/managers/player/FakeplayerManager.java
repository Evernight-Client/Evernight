package evernight.client.managers.player;

import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.misc.KillEffects.KillEffectsModule;
import evernight.client.modules.player.Freecam.FreecamModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.modules.render.LogoutSpots.LogoutSpotsModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.ServerAddressUtils;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.combat.DamageUtils;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.inventory.EnchantmentUtils;
import evernight.client.utils.network.InteractPacketUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;

/**
 * Owns the client-side fake player, its recorded movement frames and the related event handling.
 *
 * <p>Both the {@code /fakeplayer} command and the ClickGUI control panel drive the feature through
 * this manager, so the dummy keeps living while the user switches screens. The manager subscribes to
 * the event bus for the whole client lifetime; {@link #spawn(String)} and {@link #remove()} are the
 * only supported spawn entry points and may be called from the render thread.
 */
public final class FakeplayerManager implements ClientAccess {
    private static final Identifier PROTECTED_SKIN_TEXTURE = Identifier.fromNamespaceAndPath(
            "evernight", "textures/skin_protect.png");
    private static final String PROTECTED_SKIN_NAME = "Herobrine";
    private static final String DEFAULT_NAME = "Herobrine";
    private static final int FAKE_PLAYER_ENTITY_ID = -9344;
    private static final FreecamModule FREECAM = ClientContext.getModule(FreecamModule.class);
    private static final SpeedMineModule SPEED_MINE = ClientContext.getModule(SpeedMineModule.class);
    private static final LogoutSpotsModule LOGOUT_SPOTS = ClientContext.getModule(LogoutSpotsModule.class);
    private static final KillEffectsModule KILL_EFFECTS = ClientContext.getModule(KillEffectsModule.class);
    private final List<Frame> recordedFrames = new ArrayList<>();
    private final ElapsedTimer logoutSpotTimer = new ElapsedTimer();
    private SimulatedPlayer fakePlayer;
    private boolean recording;
    private boolean playing;
    private String name = DEFAULT_NAME;
    private int playbackIndex;
    private boolean logoutSpotActive;

    public FakeplayerManager() {
        ClientAccess.eventBus.subscribe(this);
    }

    /**
     * Plays the vanilla hit sound for the local attacker.
     *
     * <p>These sounds are normally broadcast by the server in {@code Player#attack}; the cancelled
     * attack packet means nothing is sent, and {@code Level#playSound} is a no-op for client-side
     * calls in this version, so the sound has to be started through {@code playLocalSound}.
     */
    private static void playAttackSound(float attackStrength, boolean critical) {
        LocalPlayer attacker = ClientAccess.evernightMinecraft.player;
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        if (attacker == null || level == null) {
            return;
        }
        SoundEvent attackSound = critical ? SoundEvents.PLAYER_ATTACK_CRIT
                : attackStrength > 0.9F ? SoundEvents.PLAYER_ATTACK_STRONG
                : SoundEvents.PLAYER_ATTACK_WEAK;
        level.playLocalSound(attacker.getX(), attacker.getY(), attacker.getZ(), attackSound,
                SoundSource.PLAYERS, 1.0F, 1.0F, false);
    }

    private static boolean isCriticalAttack(LocalPlayer attacker, float attackStrength) {
        return attackStrength > 0.9F
                && attacker.fallDistance > 0.0D
                && !attacker.onGround()
                && !attacker.onClimbable()
                && !attacker.isInWater()
                && !attacker.isMobilityRestricted()
                && !attacker.isPassenger()
                && !attacker.isSprinting();
    }

    private static Vec3 recordingPosition() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        return player.position();
    }

    public boolean hasPlayer() {
        return this.fakePlayer != null;
    }

    public String getPlayerName() {
        return this.name;
    }

    public boolean isRecording() {
        return this.recording;
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public int getRecordedFrameCount() {
        return this.recordedFrames.size();
    }

    public boolean isLogoutSpotActive() {
        return this.logoutSpotActive;
    }

    /**
     * Spawns a new fake player with the requested name.
     *
     * <p>Returns {@code false} when there is no world to spawn into, so callers can defer the spawn
     * until the next world join. An already alive dummy is kept as is. Recording and playback are
     * always reset; the name falls back to {@code Herobrine} when blank.
     */
    public boolean spawn(String requestedName) {
        if (this.isWorldUnavailable()) {
            return false;
        }
        if (this.fakePlayer != null) {
            return true;
        }
        String playerName = requestedName == null || requestedName.isBlank()
                ? DEFAULT_NAME : requestedName.trim();
        this.resetRecordedState();
        if (this.logoutSpotActive) {
            this.toggleLogoutSpot();
        }
        this.fakePlayer = new SimulatedPlayer(ClientAccess.evernightMinecraft.player,
                ClientAccess.evernightMinecraft.level, new GameProfile(UUID.randomUUID(), playerName));
        if (FREECAM.isToggled()) {
            double cameraX = FREECAM.cameraPosition.x;
            double cameraY = FREECAM.cameraPosition.y;
            double cameraZ = FREECAM.cameraPosition.z;
            float playerYaw = ClientAccess.evernightMinecraft.player.getYRot();
            float spawnPitch = ClientAccess.evernightMinecraft.player.getXRot();
            this.fakePlayer.snapTo(cameraX, cameraY, cameraZ, playerYaw, spawnPitch);
        }
        this.fakePlayer.setId(FAKE_PLAYER_ENTITY_ID);
        ClientAccess.evernightMinecraft.level.addEntity(this.fakePlayer);
        this.fakePlayer.setOnGround(ClientAccess.evernightMinecraft.player.onGround());
        this.name = playerName;
        String spawnedMessage = Translations.format("commands.Fakeplayer.Spawned", playerName);
        ChatUtils.printChat(Component.literal(spawnedMessage), ChatUtils.createSignature(-1));
        return true;
    }

    /**
     * Removes the current fake player, if any, and resets recording and playback.
     */
    public void remove() {
        this.resetRecordedState();
        if (this.fakePlayer == null) {
            return;
        }
        if (!this.isWorldUnavailable()) {
            ClientAccess.evernightMinecraft.level.removeEntity(FAKE_PLAYER_ENTITY_ID,
                    RemovalReason.DISCARDED);
        }
        this.fakePlayer = null;
        String removedName = this.name == null ? DEFAULT_NAME : this.name;
        String removedMessage = Translations.format("commands.Fakeplayer.Removed", removedName);
        ChatUtils.printChat(Component.literal(removedMessage), ChatUtils.createSignature(-1));
    }

    private void resetRecordedState() {
        this.recordedFrames.clear();
        this.recording = false;
        this.playing = false;
    }

    /**
     * Starts or stops ticking the dummy through its totem-death animation.
     */
    public void killPlayer() {
        if (this.fakePlayer == null) {
            this.notifyPlayerMissing();
            return;
        }
        KILL_EFFECTS.trackTarget(this.fakePlayer);
        this.fakePlayer.killed = true;
    }

    /**
     * Starts recording the local player's frames, or stops and keeps the ones recorded so far.
     */
    public void toggleRecording() {
        if (!this.recording && this.isWorldUnavailable()) {
            this.notifyWorldUnavailable();
            return;
        }
        this.playing = false;
        this.recording = !this.recording;
        if (this.recording) {
            this.recordedFrames.clear();
            MutableComponent startedMessage = Component.literal(Translations.ui(
                    "commands.Fakeplayer.RecordingStarted"));
            ChatUtils.printChat(startedMessage, ChatUtils.createSignature(-1));
        } else {
            MutableComponent stoppedMessage = Component.literal(Translations.ui(
                    "commands.Fakeplayer.RecordingStopped"));
            ChatUtils.printChat(stoppedMessage, ChatUtils.createSignature(-1));
        }
    }

    /**
     * Starts or stops replaying the recorded frames on the current dummy.
     */
    public void togglePlayback() {
        if (this.fakePlayer == null) {
            this.notifyPlayerMissing();
            return;
        }
        this.playbackIndex = 0;
        this.playing = !this.playing;
        this.recording = false;
    }

    /**
     * Adds a logout spot at the dummy's position, or removes the active one.
     */
    public void toggleLogoutSpot() {
        if (!this.logoutSpotActive && this.isWorldUnavailable()) {
            this.notifyWorldUnavailable();
            return;
        }
        if (this.logoutSpotActive) {
            this.logoutSpotActive = false;
            LOGOUT_SPOTS.removeSpot("Herobrine");
            ChatUtils.printChat(Component.literal(Translations.ui(
                    "commands.Fakeplayer.LogoutSpotRemoved")), -1);
        } else {
            SimulatedPlayer player = this.fakePlayer != null ? this.fakePlayer : new SimulatedPlayer(
                    ClientAccess.evernightMinecraft.player, ClientAccess.evernightMinecraft.level,
                    new GameProfile(UUID.randomUUID(), this.name));
            String address = ServerAddressUtils.getCurrentServerAddress();
            this.logoutSpotActive = true;
            LOGOUT_SPOTS.putSpot("Herobrine", LogoutSpotsModule.LogoutSpot.capturePlayer(player, address));
            this.logoutSpotTimer.reset();
            ChatUtils.printChat(Component.literal(Translations.ui(
                    "commands.Fakeplayer.LogoutSpotAdded")), -1);
        }
        if (this.fakePlayer != null) {
            ClientAccess.evernightMinecraft.level.removeEntity(this.fakePlayer.getId(),
                    RemovalReason.DISCARDED);
            this.fakePlayer = null;
        }
    }

    private void notifyWorldUnavailable() {
        ChatUtils.printChat(Component.literal(Translations.ui(
                        "modules.Fakeplayer.NoWorld")),
                ChatUtils.createSignature(-1));
    }

    private void notifyPlayerMissing() {
        ChatUtils.printChat(Component.literal(Translations.ui(
                        "modules.Fakeplayer.NoPlayer")),
                ChatUtils.createSignature(-1));
    }

    private BlockPos getMiningIgnorePosition() {
        float miningDamage = SPEED_MINE.getMiningProgress();
        Setting<Float> damageSetting = SPEED_MINE.damageSetting;
        Float damageThreshold = damageSetting.getValue();
        float normalizedMiningDamage = miningDamage / damageThreshold;
        if (SPEED_MINE.isToggled()) {
            if (!(normalizedMiningDamage < 1.0F)) {
                return SPEED_MINE.getMiningPos();
            }
        }
        return null;
    }

    @EventHandler(priority = 100)
    private void interceptFakePlayerAttack(PacketEvent.Send event) {
        if (this.fakePlayer == null) {
            return;
        }
        if (event.getPacket() instanceof ClientboundDisconnectPacket
                || event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
            this.fakePlayer = null;
            return;
        }
        if (!(event.getPacket() instanceof ServerboundInteractPacket interactionPacket)) {
            return;
        }
        if (InteractPacketUtils.getAction(interactionPacket) != InteractPacketUtils.Action.ATTACK) {
            return;
        }
        Entity target = InteractPacketUtils.getEntity(interactionPacket);
        if (target == null || target.getId() != this.fakePlayer.getId()) {
            return;
        }
        event.cancel();
        SimulatedPlayer attackedPlayer = this.fakePlayer;
        ClientAccess.evernightMinecraft.execute(() -> this.simulatePlayerAttack(attackedPlayer));
    }

    /**
     * Applies the local player's melee damage to the dummy.
     *
     * <p>The damage is rebuilt from the same values vanilla uses in {@code Player#attack}: the
     * attack-damage attribute, the attack-strength scale, the held item bonus, Sharpness and the
     * critical multiplier. {@link SimulatedPlayer#simulateHurt} then runs the armor, protection,
     * resistance and absorption pipeline and plays the hurt sound, because a client-only entity
     * never receives the server damage event that normally does that.
     */
    private void simulatePlayerAttack(SimulatedPlayer target) {
        LocalPlayer attacker = ClientAccess.evernightMinecraft.player;
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        if (attacker == null || level == null || this.fakePlayer != target) {
            return;
        }
        ItemStack weapon = attacker.getWeaponItem();
        DamageSource damageSource = weapon.getDamageSource(attacker,
                () -> level.damageSources().playerAttack(attacker));
        float attackStrength = attacker.getAttackStrengthScale(0.5F);
        float attackDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = attackDamage * (0.2F + attackStrength * attackStrength * 0.8F);
        damage += weapon.getItem().getAttackDamageBonus(target, damage, damageSource);
        int sharpnessLevel = EnchantmentUtils.getLevel(Enchantments.SHARPNESS, weapon);
        float enchantmentDamage = sharpnessLevel > 0
                ? attackStrength * (0.5F * sharpnessLevel + 0.5F) : 0.0F;
        boolean critical = isCriticalAttack(attacker, attackStrength);
        if (critical) {
            damage *= 1.5F;
        }
        if (target.simulateHurt(damageSource, damage + enchantmentDamage)) {
            playAttackSound(attackStrength, critical);
        }
    }

    @EventHandler
    private void clearFakePlayerOnDisconnect(MinecraftDisconnectEvent event) {
        this.fakePlayer = null;
    }

    @EventHandler
    private void updateRecordingAndPlayback(LocalPlayerSendPositionEvent event) {
        if (this.fakePlayer != null) {
            int deathTicks = this.fakePlayer.deathTime;
            if (deathTicks > 6) {
                this.fakePlayer = null;
            }
        }
        if (this.logoutSpotActive) {
            TimeUnit expirationUnit = TimeUnit.MINUTES;
            if (this.logoutSpotTimer.hasElapsedAndReset(5L, expirationUnit)) {
                this.toggleLogoutSpot();
            }
        }
        if (event.getStage() == EventStage.Post && this.recording) {
            this.recordedFrames.add(new Frame(recordingPosition(),
                    ClientAccess.evernightMinecraft.player.getYRot(),
                    ClientAccess.evernightMinecraft.player.getXRot(),
                    ClientAccess.evernightMinecraft.player.getYHeadRot(),
                    ClientAccess.evernightMinecraft.player.getDeltaMovement(),
                    ClientAccess.evernightMinecraft.player.isShiftKeyDown(),
                    ClientAccess.evernightMinecraft.player.onGround()));
        } else if (event.getStage() == EventStage.Pre && this.fakePlayer != null && this.playing
                && !this.recordedFrames.isEmpty()) {
            this.playbackIndex = (this.playbackIndex + 1) % this.recordedFrames.size();
            Frame frame = this.recordedFrames.get(this.playbackIndex);
            float framePitch = frame.pitch();
            float frameYaw = frame.yaw();
            Vec3 framePosition = frame.position();
            this.fakePlayer.moveOrInterpolateTo(framePosition, frameYaw, framePitch);
            Vec3 frameVelocity = frame.velocity();
            this.fakePlayer.setDeltaMovement(frameVelocity);
            this.fakePlayer.setYRot(frameYaw);
            this.fakePlayer.setXRot(framePitch);
            this.fakePlayer.setYHeadRot(frame.headYaw());
            this.fakePlayer.setShiftKeyDown(frame.sneaking());
            this.fakePlayer.setOnGround(frame.onGround());
            if (this.fakePlayer.isShiftKeyDown()) {
                this.fakePlayer.setPose(Pose.CROUCHING);
            } else {
                this.fakePlayer.setPose(Pose.STANDING);
            }
        }
    }

    @EventHandler
    private void simulateExplosionDamage(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundLoginPacket) {
            this.fakePlayer = null;
        }
        if (this.fakePlayer != null) {
            if (event.getPacket() instanceof ClientboundExplodePacket explosionPacket) {
                Vec3 xCenter = explosionPacket.center();
                double explosionX = xCenter.x();
                Vec3 yCenter = explosionPacket.center();
                double explosionY = yCenter.y();
                Vec3 zCenter = explosionPacket.center();
                double explosionZ = zCenter.z();
                Vec3 explosionPosition = this.fakePlayer.position();
                Vec3 distanceCenter = new Vec3(explosionX, explosionY, explosionZ);
                double explosionDistance = explosionPosition.distanceTo(distanceCenter);
                double normalizedDistance = explosionDistance / 12.0D;
                if (normalizedDistance > 1.0D) {
                    return;
                }
                explosionPosition = explosionPacket.center();
                AABB damageBounds = this.fakePlayer.getBoundingBox();
                BlockPos miningIgnorePosition = this.getMiningIgnorePosition();
                Object additionalIgnorePosition = null;
                boolean damageFlag = true;
                double explosionPower = 6.0D;
                float damage = DamageUtils.getExplosionDamage(
                        explosionPosition, this.fakePlayer, damageBounds, explosionPower, damageFlag,
                        miningIgnorePosition, (BlockPos) additionalIgnorePosition
                );
                if (this.fakePlayer.hurtTime == 0) {
                    float healthAfterDamage = EntityUtils.getHealthWithAbsorption(this.fakePlayer) - damage;
                    float remainingTotalHealth = Math.max(healthAfterDamage, 0.0F);
                    float maximumHealth = this.fakePlayer.getMaxHealth();
                    if (remainingTotalHealth > maximumHealth) {
                        this.fakePlayer.setHealth(maximumHealth);
                        float remainingAbsorption = remainingTotalHealth - maximumHealth;
                        this.fakePlayer.setAbsorptionAmount(remainingAbsorption);
                    } else {
                        this.fakePlayer.setHealth(remainingTotalHealth);
                        this.fakePlayer.setAbsorptionAmount(0.0F);
                    }
                    DamageSource explosionDamageSource = ClientAccess.evernightMinecraft.level.damageSources()
                            .explosion(null);
                    this.fakePlayer.handleDamageEvent(explosionDamageSource);
                    this.fakePlayer.playHurtSoundLocally(explosionDamageSource);
                }
            }
        }
    }

    private record Frame(Vec3 position, float yaw, float pitch, float headYaw, Vec3 velocity, boolean sneaking,
                         boolean onGround) {
    }

    public static class SimulatedPlayer extends RemotePlayer {
        private final boolean useProtectedSkin;
        private boolean killed;

        public SimulatedPlayer(Player source, ClientLevel level, GameProfile profile) {
            super(level, profile);
            this.useProtectedSkin = profile.name().equals(PROTECTED_SKIN_NAME);
            this.copyPosition(source);
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
            this.yHeadRot = source.yHeadRot;
            this.yHeadRotO = this.yHeadRot;
            this.yBodyRot = source.yBodyRot;
            this.yBodyRotO = this.yBodyRot;
            this.setPose(source.getPose());
            AttributeInstance maxAbsorption = this.getAttribute(Attributes.MAX_ABSORPTION);
            if (maxAbsorption != null) {
                maxAbsorption.setBaseValue(4.0D);
            }
            this.setHealth(20.0F);
            this.getInventory().replaceWith(source.getInventory());
            this.setInvulnerable(false);
            if (source.isFallFlying()) {
                this.startFallFlying();
            }
        }

        @Override
        public boolean isAlive() {
            return !this.killed;
        }

        @Override
        public void tick() {
            if (this.killed) {
                int previousDeathTime = this.deathTime++;
                if (previousDeathTime > 6) {
                    this.remove(RemovalReason.KILLED);
                }
                this.setAbsorptionAmount(0.0F);
                this.setHealth(0.0F);
                return;
            }
            ItemStack capturedOffhand = this.getOffhandItem();
            if (!capturedOffhand.is(Items.TOTEM_OF_UNDYING)) {
                Inventory capturedInventory = this.getInventory();
                ItemStack replacementTotem = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                capturedInventory.setItem(40, replacementTotem);
            }
            super.tick();
        }

        /**
         * Applies one melee hit to this client-only player.
         *
         * <p>The server normally applies damage and then sends a damage event, which is what makes
         * the client play the hurt animation and sound. A simulated player never receives that
         * packet, so the same pipeline is rebuilt here: invulnerability window, armor, protection,
         * resistance, absorption, the hurt animation and finally a locally played hurt sound.
         *
         * @return whether the hit counted, mirroring {@code LivingEntity.hurtServer}
         */
        public boolean simulateHurt(DamageSource damageSource, float damage) {
            if (this.killed || this.isDeadOrDying() || damage <= 0.0F) {
                return false;
            }
            if (this.invulnerableTime > 10) {
                float additionalDamage = damage - this.lastHurt;
                if (additionalDamage <= 0.0F) {
                    return false;
                }
                this.lastHurt = damage;
                this.applySimulatedDamage(damageSource, additionalDamage);
                return true;
            }
            this.lastHurt = damage;
            this.invulnerableTime = 20;
            this.hurtTime = this.hurtDuration = 10;
            this.applySimulatedDamage(damageSource, damage);
            this.handleDamageEvent(damageSource);
            this.playHurtSoundLocally(damageSource);
            return true;
        }

        /**
         * Plays the hurt sound for the local client.
         *
         * <p>{@code LivingEntity#handleDamageEvent} calls {@code Entity#playSound}, which passes
         * {@code null} to {@code Level#playSound}; {@code ClientLevel} drops such calls, so a
         * client-only entity has to start the sound through {@code playLocalSound} instead.
         */
        private void playHurtSoundLocally(DamageSource damageSource) {
            SoundEvent hurtSound = this.getHurtSound(damageSource);
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            if (hurtSound == null || level == null) {
                return;
            }
            float pitch = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
            level.playLocalSound(this, hurtSound, SoundSource.PLAYERS, this.getSoundVolume(), pitch);
        }

        private void applySimulatedDamage(DamageSource damageSource, float damage) {
            float damageAfterArmor = this.getDamageAfterArmorAbsorb(damageSource, damage);
            float damageAfterEffects = this.getDamageAfterMagicAbsorb(damageSource, damageAfterArmor);
            if (!damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)
                    && !damageSource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
                int protection = this.getProtectionLevel();
                if (protection > 0) {
                    damageAfterEffects = CombatRules.getDamageAfterMagicAbsorb(damageAfterEffects, protection);
                }
            }
            float absorption = this.getAbsorptionAmount();
            float healthDamage = Math.max(damageAfterEffects - absorption, 0.0F);
            this.setAbsorptionAmount(absorption - (damageAfterEffects - healthDamage));
            if (healthDamage <= 0.0F) {
                return;
            }
            this.setHealth(this.getHealth() - healthDamage);
        }

        /**
         * Sums the vanilla Protection levels of the worn armor.
         *
         * <p>{@code LivingEntity#getDamageAfterMagicAbsorb} can only read protection enchantments
         * through a {@code ServerLevel}, so the client-side simulation has to add them back.
         */
        private int getProtectionLevel() {
            int protection = 0;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    protection += EnchantmentUtils.getLevel(Enchantments.PROTECTION, slot, this);
                }
            }
            return Math.min(protection, 20);
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean isAttackable() {
            return true;
        }

        @Override
        public boolean ignoreExplosion(Explosion ignoredExplosion) {
            return false;
        }

        @Override
        public boolean fireImmune() {
            return true;
        }

        @Override
        public void setHealth(float health) {
            super.setHealth(health);
            if (this.getHealth() <= 0.0F && !this.killed) {
                super.setHealth(9.0F);
                this.removeAllEffects();
                this.setAbsorptionAmount(4.0F);
                Holder regeneration = MobEffects.REGENERATION;
                MobEffectInstance regenerationEffect = new MobEffectInstance(regeneration, 900, 1);
                this.addEffect(regenerationEffect);
                Holder fireResistance = MobEffects.FIRE_RESISTANCE;
                MobEffectInstance fireResistanceEffect = new MobEffectInstance(fireResistance, 800, 0);
                this.addEffect(fireResistanceEffect);
                ParticleEngine particles = ClientAccess.evernightMinecraft.particleEngine;
                SimpleParticleType totemParticles = ParticleTypes.TOTEM_OF_UNDYING;
                particles.createTrackingEmitter(this, totemParticles, 30);
                ClientLevel capturedSoundLevel = ClientAccess.evernightMinecraft.level;
                LocalPlayer capturedSoundPlayer = ClientAccess.evernightMinecraft.player;
                BlockPos capturedSoundPosition = this.blockPosition();
                SoundEvent totemSound = SoundEvents.TOTEM_USE;
                SoundSource playerSoundCategory = SoundSource.PLAYERS;
                capturedSoundLevel.playSound(capturedSoundPlayer, capturedSoundPosition, totemSound,
                        playerSoundCategory, 1.0F, 1.0F);
                EventBus capturedEventBus = ClientAccess.eventBus;
                PacketEvent.Receive totemEvent = new PacketEvent.Receive(
                        new ClientboundEntityEventPacket(this, (byte) 35));
                capturedEventBus.post(totemEvent);
            }
        }

        @Override
        protected void tickDeath() {
            this.deathTime = 0;
        }

        @Override
        protected void doPush(Entity ignoredEntity) {
        }

        @Override
        public PlayerSkin getSkin() {
            if (this.useProtectedSkin) {
                PlayerSkin originalSkin = super.getSkin();
                ResourceTexture protectedTexture = new ResourceTexture(PROTECTED_SKIN_TEXTURE,
                        PROTECTED_SKIN_TEXTURE);
                return new PlayerSkin(protectedTexture, originalSkin.cape(), originalSkin.elytra(),
                        PlayerModelType.WIDE, originalSkin.secure());
            }
            return super.getSkin();
        }
    }
}
