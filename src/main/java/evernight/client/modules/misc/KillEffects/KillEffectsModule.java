package evernight.client.modules.misc.KillEffects;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.audio.BuiltinSound;
import evernight.client.audio.SoundClip;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.managers.audio.SoundManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.ClientLightningState;
import evernight.client.utils.collections.NamedIdentifier;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.PlayerAttackEvent;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.IdentifierSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.settings.types.StringListSetting;
import evernight.client.settings.types.StringSetting;

public class KillEffectsModule extends Module {
    public final Setting<Boolean> thunderSetting;
    public final Setting<Color> colorSetting;
    protected final Setting<Boolean> killSaySetting;
    protected final Setting<Float> delaySetting;
    protected final Setting<String> pathSetting;
    protected final StringListSetting killSayMessagesSetting;
    protected final Setting<Boolean> onlyEnemiesSetting;
    protected final Setting<Boolean> randomSetting;
    protected final Setting<Boolean> refreshSetting;
    protected final Setting<Set<ParticleType<?>>> particleTypesSetting;
    final Setting<Boolean> killSoundSetting;
    final Setting<Boolean> killStreakSetting;
    private final Setting<NamedIdentifier> soundSetting;
    private final Setting<NamedIdentifier> selfSoundSetting;
    private final Setting<Float> killSoundVolumeSetting;
    private final Setting<Float> killStreakVolumeSetting;
    private final Setting<Integer> lightningsSetting;
    private final Setting<KillEffectsParticlesChoice> particlesSetting;
    private final Setting<Boolean> ignoreNakedsSetting;
    private final Setting<Boolean> selfSetting;
    private final KillSayMessages killSayMessages;
    private final ConcurrentHashMap<UUID, Integer> trackedTargets;
    private final ObjectList<UUID> expiredTargets;
    private int killStreak;

    public KillEffectsModule() {
        super("KillEffects", ModuleCategory.MISC);
        Boolean killSoundDefault = false;
        BooleanSetting killSoundDefinition = new BooleanSetting(("KillSound"), killSoundDefault);
        killSoundDefinition.markVisibilityDependency();
        this.killSoundSetting = this.add(killSoundDefinition);
        Predicate<Object> soundPredicate = ignored -> this.killSoundSetting.isExpanded();
        BuiltinSound defaultKillSound = BuiltinSound.BEEP;
        NamedIdentifier defaultSound = defaultKillSound.getIdentifier();
        this.soundSetting = this.add(new IdentifierSetting(("Sound"), defaultSound, soundPredicate));
        Predicate<Object> selfSoundPredicate = ignored -> this.killSoundSetting.isExpanded();
        BuiltinSound bodySplatSound = BuiltinSound.BODYSPLAT;
        NamedIdentifier defaultSelfSound = bodySplatSound.getIdentifier();
        this.selfSoundSetting = this.add(new IdentifierSetting(("SelfSound"), defaultSelfSound, selfSoundPredicate));
        Predicate<Object> killVolumePredicate = ignored -> this.killSoundSetting.isExpanded();
        this.killSoundVolumeSetting = this.add(new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F, killVolumePredicate));
        Boolean killStreakDefault = false;
        BooleanSetting killStreakDefinition = new BooleanSetting(("KillStreak"), killStreakDefault);
        killStreakDefinition.markVisibilityDependency();
        this.killStreakSetting = this.add(killStreakDefinition);
        Predicate<Object> streakVolumePredicate = ignored -> this.killStreakSetting.isExpanded();
        this.killStreakVolumeSetting = this.add(new NumberSetting<>("Volume", 0.4F, 0.0F, 1.0F, streakVolumePredicate));
        Boolean killSayDefault = false;
        BooleanSetting killSayDefinition = new BooleanSetting(("KillSay"), killSayDefault);
        killSayDefinition.markVisibilityDependency();
        Setting<Boolean> registeredKillSay = this.add(killSayDefinition);
        this.killSaySetting = registeredKillSay;
        NumberSetting<Float> delayDefinition = new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F);
        delayDefinition.withUnit(("s"));
        this.delaySetting = this.add(delayDefinition.dependsOn(registeredKillSay));
        StringSetting pathDefinition = new StringSetting(("Path"), ("killsay.txt"));
        this.pathSetting = this.add(pathDefinition.dependsOn(registeredKillSay));
        StringListSetting messagesDefinition = new StringListSetting("Messages");
        // The killsay text file stays the source of truth, so the in-memory list is not persisted.
        messagesDefinition.setExcludedFromConfig(true);
        messagesDefinition.dependsOn(registeredKillSay);
        this.killSayMessagesSetting = this.add(messagesDefinition);
        Boolean onlyEnemiesDefault = false;
        BooleanSetting onlyEnemiesDefinition = new BooleanSetting(("OnlyEnemies"), onlyEnemiesDefault);
        this.onlyEnemiesSetting = this.add(onlyEnemiesDefinition.dependsOn(registeredKillSay));
        Boolean randomDefault = true;
        BooleanSetting randomDefinition = new BooleanSetting(("Random"), randomDefault);
        this.randomSetting = this.add(randomDefinition.dependsOn(registeredKillSay));
        Boolean refreshDefault = false;
        BooleanSetting refreshDefinition = new BooleanSetting(("Refresh"), refreshDefault);
        this.refreshSetting = this.add(refreshDefinition.dependsOn(registeredKillSay));
        Boolean thunderDefault = true;
        BooleanSetting thunderDefinition = new BooleanSetting(("Thunder"), thunderDefault);
        thunderDefinition.markVisibilityDependency();
        Setting<Boolean> registeredThunder = this.add(thunderDefinition);
        this.thunderSetting = registeredThunder;
        NumberSetting<Integer> lightningsDefinition = new NumberSetting<>("Lightnings", 1, 1, 4);
        this.lightningsSetting = this.add(lightningsDefinition.dependsOn(registeredThunder));
        Color defaultColor = new Color(-8355712, true);
        ColorSetting colorDefinition = new ColorSetting(("Color"), defaultColor);
        this.colorSetting = this.add(colorDefinition.dependsOn(registeredThunder));
        KillEffectsParticlesChoice defaultParticles = KillEffectsParticlesChoice.OFF;
        this.particlesSetting = this.add(new EnumSetting<>(("Particles"), defaultParticles));
        Registry particleRegistry = BuiltInRegistries.PARTICLE_TYPE;
        ParticleType[] defaultParticleTypes = new ParticleType[0];
        this.particleTypesSetting = this.add(new RegistrySetSetting<>(("ParticleTypes"), particleRegistry, defaultParticleTypes));
        Boolean targetsDefault = false;
        BooleanSetting targetsDefinition = new BooleanSetting(("Targets"), targetsDefault);
        targetsDefinition.markAsGroup();
        targetsDefinition.markVisibilityDependency();
        Setting<Boolean> registeredTargets = this.add(targetsDefinition);
        Boolean ignoreNakedsDefault = true;
        BooleanSetting ignoreNakedsDefinition = new BooleanSetting(("IgnoreNakeds"), ignoreNakedsDefault);
        this.ignoreNakedsSetting = this.add(ignoreNakedsDefinition.dependsOn(registeredTargets));
        Boolean selfDefault = false;
        BooleanSetting selfDefinition = new BooleanSetting(("Self"), selfDefault);
        this.selfSetting = this.add(selfDefinition.dependsOn(registeredTargets));
        this.killSayMessages = new KillSayMessages(this);
        this.trackedTargets = new ConcurrentHashMap<>();
        this.expiredTargets = ObjectLists.synchronize(new ObjectArrayList<>());
        this.killStreak = 0;
        Setting<?> callbackOption = this.killStreakVolumeSetting;
        callbackOption.setConfigName(("KillStreakVolume"));
        this.killSayMessagesSetting.setChangeCallback(this.killSayMessages::onSettingChanged);
        // Reload the file right before the editor opens, so it never edits a stale snapshot.
        this.killSayMessagesSetting.withPrepareAction(() -> this.killSayMessages.reloadMessages(false));
        // Resetting module settings must not clear the killsay file.
        this.killSayMessagesSetting.withResetAction(() -> this.killSayMessages.reloadMessages(false));
        this.killSaySetting.setChangeCallback(() -> this.killSayMessages.reloadMessages(false));
        this.pathSetting.setChangeCallback(() -> this.killSayMessages.reloadMessages(true));
        this.refreshSetting.setChangeCallback(() -> {
            Setting<Boolean> refreshOption = this.refreshSetting;
            Boolean refresh = refreshOption.getValue();
            if (refresh) {
                this.killSayMessages.reloadMessages(true);
                this.refreshSetting.setValue(false);
            }
        });
        this.randomSetting.setChangeCallback(this.killSayMessages::resetSequence);
        callbackOption = this.particleTypesSetting;
        Predicate customParticlesPredicate = ignoredParticleOption -> {
            return this.particlesSetting.getValue() == KillEffectsParticlesChoice.CUSTOM;
        };
        callbackOption.addVisibilityPredicate(customParticlesPredicate);
        this.setDrawn(false);
    }

    private void spawnKillParticles(Vec3 position) {
        Setting<KillEffectsParticlesChoice> particleOption = this.particlesSetting;
        KillEffectsParticlesChoice particleChoiceSnapshot = particleOption.getValue();
        ParticleOptions particle = particleChoiceSnapshot.chooseParticle(this);
        if (particle == null) {
            return;
        }
        Setting<KillEffectsParticlesChoice> emissionOption = this.particlesSetting;
        KillEffectsParticlesChoice emissionChoice = emissionOption.getValue();
        emissionChoice.emitParticles(particle, position);
    }

    @Override
    public void onEnable() {
        this.killStreak = 0;
        this.killSayMessages.reloadMessages((true));
    }

    private void spawnKillLightning(Vec3 position) {
        Setting<Boolean> thunderOption = this.thunderSetting;
        Boolean spawnThunder = thunderOption.getValue();
        if (!spawnThunder) {
            return;
        }
        for (int index = 0; ; index++) {
            Setting<Integer> lightningsOption = this.lightningsSetting;
            Integer lightningCount = lightningsOption.getValue();
            if (index >= lightningCount) {
                return;
            }
            LightningBolt lightning = new LightningBolt(
                    EntityType.LIGHTNING_BOLT, ClientAccess.evernightMinecraft.level);
            lightning.setPos(position);
            lightning.snapTo(position);
            ((ClientLightningState) lightning).evernight$setClient(true);
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            level.addEntity(lightning);
        }
    }

    @EventHandler
    private void onPlayerAttackEvent(PlayerAttackEvent event) {
        if (event.getStage() == EventStage.Post) {
            return;
        }
        if (this.isWorldUnavailable()) {
            return;
        }
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player == player) {
            return;
        }
        SocialRegistry friendManager = ClientContext.socialRegistry;
        if (friendManager.isFriendPlayer(player)) {
            return;
        }
        if (!PlayerStateUtils.hasArmor(player)) {
            Setting<Boolean> ignoreNakedsOption = this.ignoreNakedsSetting;
            Boolean ignoreNakeds = ignoreNakedsOption.getValue();
            if (ignoreNakeds) {
                return;
            }
        }
        if (EntityUtils.getHealthWithAbsorption(player) > 0.0F) {
            try {
                ConcurrentHashMap<UUID, Integer> targetsSnapshot = this.trackedTargets;
                UUID uuidSnapshot = player.getUUID();
                Integer trackedTick = ClientAccess.evernightMinecraft.player.tickCount;
                targetsSnapshot.put(uuidSnapshot, trackedTick);
            } catch (Exception trackingFailure) {
            }
        }
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (this.killStreak == -1) {
            this.killStreak = 0;
            Setting<Boolean> selfOption = this.selfSetting;
            Boolean selfEnabled = selfOption.getValue();
            if (!selfEnabled) {
                return;
            }
            Setting<Boolean> selfKillSoundOption = this.killSoundSetting;
            Boolean selfKillSoundEnabled = selfKillSoundOption.getValue();
            if (selfKillSoundEnabled) {
                Setting<Float> selfVolumeCheckOption = this.killSoundVolumeSetting;
                Float selfVolumeCheck = selfVolumeCheckOption.getValue();
                if (selfVolumeCheck > 0.0F) {
                    SoundManager selfSoundManagerSnapshot = ClientContext.soundManager;
                    Setting<NamedIdentifier> selfSoundOption = this.selfSoundSetting;
                    NamedIdentifier selfSoundId = selfSoundOption.getValue();
                    SoundClip selfSound = selfSoundManagerSnapshot.getSound(selfSoundId);
                    if (selfSound != null) {
                        Setting<Float> selfVolumeReadOption = this.killSoundVolumeSetting;
                        float selfPlaybackVolume = selfVolumeReadOption.getValue();
                        selfSound.play(selfPlaybackVolume);
                    }
                }
            }
            LocalPlayer lightningLocalPlayer = ClientAccess.evernightMinecraft.player;
            Vec3 selfPositionSnapshot = lightningLocalPlayer.position();
            this.spawnKillLightning(selfPositionSnapshot);
            LocalPlayer particlesLocalPlayer = ClientAccess.evernightMinecraft.player;
            selfPositionSnapshot = particlesLocalPlayer.position();
            this.spawnKillParticles(selfPositionSnapshot);
        }
        this.expiredTargets.clear();
        synchronized (this.trackedTargets) {
            KeySetView<UUID, Integer> targetKeySet = this.trackedTargets.keySet();
            Iterator<UUID> targetIds = targetKeySet.iterator();
            while (targetIds.hasNext()) {
                UUID uuid = targetIds.next();
                try {
                    ClientLevel targetLevel = ClientAccess.evernightMinecraft.level;
                    Player player = targetLevel.getPlayerByUUID(uuid);
                    if (player == null) {
                        this.expiredTargets.add(uuid);
                        continue;
                    }
                    int trackedAtTick = ClientAccess.evernightMinecraft.player.tickCount;
                    Integer trackedTick = this.trackedTargets.get(uuid);
                    int ageTicks = trackedAtTick - trackedTick;
                    if (ageTicks > (200)) {
                        this.expiredTargets.add(uuid);
                        continue;
                    }
                    if (!(player.getHealth() <= 0.0F)) {
                        continue;
                    }
                    this.expiredTargets.add(uuid);
                    this.killStreak++;
                    KillSayMessages messagesSnapshot = this.killSayMessages;
                    String playerName = player.getName().getString();
                    messagesSnapshot.sendKillMessage(player, playerName);
                    Vec3 lightningPositionSnapshot = player.position();
                    this.spawnKillLightning(lightningPositionSnapshot);
                    Vec3 particlesPositionSnapshot = player.position();
                    this.spawnKillParticles(particlesPositionSnapshot);
                    Setting<Boolean> killSoundOption = this.killSoundSetting;
                    Boolean killSoundEnabled = killSoundOption.getValue();
                    if (killSoundEnabled) {
                        Setting<Float> killVolumeCheckOption = this.killSoundVolumeSetting;
                        Float killVolumeCheck = killVolumeCheckOption.getValue();
                        if (killVolumeCheck > 0.0F) {
                            SoundManager killSoundManagerSnapshot = ClientContext.soundManager;
                            Setting<NamedIdentifier> killSoundIdOption = this.soundSetting;
                            NamedIdentifier killSoundId = killSoundIdOption.getValue();
                            SoundClip killSound = killSoundManagerSnapshot.getSound(killSoundId);
                            if (killSound != null) {
                                Setting<Float> killVolumeReadOption = this.killSoundVolumeSetting;
                                float killPlaybackVolume = killVolumeReadOption.getValue();
                                killSound.play(killPlaybackVolume);
                            }
                        }
                    }
                    Setting<Boolean> streakOption = this.killStreakSetting;
                    Boolean streakEnabled = streakOption.getValue();
                    if (streakEnabled) {
                        int currentStreak = this.killStreak;
                        if (currentStreak >= (2)) {
                            Setting<Float> streakVolumeCheckOption = this.killStreakVolumeSetting;
                            Float streakVolumeCheck = streakVolumeCheckOption.getValue();
                            if (streakVolumeCheck > 0.0F) {
                                SoundManager streakSoundManagerSnapshot = ClientContext.soundManager;
                                int streakCount = this.killStreak;
                                SoundClip streakSound = streakSoundManagerSnapshot.getKillstreakSound(streakCount);
                                if (streakSound != null) {
                                    Setting<Float> streakVolumeReadOption = this.killStreakVolumeSetting;
                                    float streakPlaybackVolume = streakVolumeReadOption.getValue();
                                    streakSound.play(streakPlaybackVolume);
                                }
                            }
                        }
                    }
                } catch (Exception trackingFailure) {
                }
            }
        }
        synchronized (this.expiredTargets) {
            try {
                ObjectListIterator<UUID> expiredIterator = this.expiredTargets.iterator();
                while (expiredIterator.hasNext()) {
                    UUID expiredUuid = expiredIterator.next();
                    this.trackedTargets.remove(expiredUuid);
                }
            } catch (Exception removalFailure) {
            }
        }
    }

    @EventHandler
    private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        this.killStreak = 0;
    }

    public void trackTarget(Player player) {
        if (player == ClientAccess.evernightMinecraft.player) {
            return;
        }
        SocialRegistry friendManagerSnapshot = ClientContext.socialRegistry;
        String nameSnapshot = player.getName().getString();
        if (friendManagerSnapshot.isFriendName(nameSnapshot)) {
            return;
        }
        try {
            ConcurrentHashMap<UUID, Integer> targetsSnapshot = this.trackedTargets;
            UUID uuidSnapshot = player.getUUID();
            Integer trackedTick = ClientAccess.evernightMinecraft.player.tickCount;
            targetsSnapshot.put(uuidSnapshot, trackedTick);
        } catch (Exception trackingFailure) {
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
            this.killStreak = -1;
        }
    }
}