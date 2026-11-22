package evernight.client.modules.movement.Fireworks;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.combat.AutoArmor.AutoArmorModule;
import evernight.client.modules.combat.AutoMace.AutoMaceModule;
import evernight.client.modules.exploit.RocketExtender.RocketExtenderModule;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.KeyUtils;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.MultiPlayerGameModeUseItemEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.render.SoundEnginePlayEvent;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public final class FireworksModule extends Module {
    public static final AtomicBoolean temporaryElytraSwapActive = new AtomicBoolean(false);
    private static AutoArmorModule autoArmorModule;
    private static AutoMaceModule autoMaceModule;
    private static RocketExtenderModule rocketExtenderModule;
    private static ElytraFlyModule elytraFlyModule;
    public final Setting<Boolean> antiWasteSetting;
    public final Setting<Boolean> softLagSetting;
    public final Setting<Boolean> fakeRocketSetting;
    public final Setting<Boolean> boostSetting;
    public final Setting<Boolean> withElytraFlySetting;
    public final Setting<Boolean> grimSetting;
    public final Setting<Boolean> directionalSetting;
    public final Setting<Float> boostFactorSetting;
    public final Setting<Boolean> autoLaunchSetting;
    public final Setting<Boolean> fastLaunchSetting;
    public final ElapsedTimer rocketUseTimer;
    public final ElapsedTimer fakeRocketTimer;
    final Setting<ModeChoice> modeSetting;
    final Setting<SwapChoice> swapSetting;
    private final Setting<Boolean> onlyElytraFlySetting;
    private final Setting<Boolean> onlyFirstSetting;
    private final Setting<Boolean> awaitSetting;
    private final Setting<Float> delaySetting;
    private final Setting<Integer> ticksSetting;
    private final Setting<Boolean> swapBackSetting;
    private final Setting<Boolean> autoPhaseSetting;
    public boolean resetRocketAge;
    private boolean rocketLaunchedDuringFlight;
    private int fastLaunchTicksRemaining;
    private boolean swapBackPending;
    private boolean fastLaunchWasActive;
    private int ceilingFlightTicks;
    private int swappedElytraSlot;
    private int fakeRocketLifetimeTicks;
    private int fakeRocketEntityId;

    public FireworksModule() {
        super("Fireworks", ModuleCategory.MOVEMENT, "rockets");
        this.antiWasteSetting = this.add(new BooleanSetting("AntiWaste", false));
        this.softLagSetting = this.add(new BooleanSetting("SoftLag", false));
        this.fakeRocketSetting = this.add(new BooleanSetting("FakeRocket", false));
        this.boostSetting = this.add(new BooleanSetting("Boost", false).markVisibilityDependency());
        this.withElytraFlySetting = this.add(new BooleanSetting("WithElytraFly", true).dependsOn(this.boostSetting));
        this.grimSetting = this.add(new BooleanSetting("Grim", false).dependsOn(this.boostSetting));
        Predicate<Object> directionalVisibility = ignored -> !(this.grimSetting.getValue());
        this.directionalSetting = this.add(new BooleanSetting("Directional", false, directionalVisibility).dependsOn(this.boostSetting));
        Predicate<Object> boostFactorVisibility = ignored -> !(this.grimSetting.getValue());
        this.boostFactorSetting = this.add(new NumberSetting<>("BoostFactor", 0.0F, 0.0F, 3.0F, boostFactorVisibility).dependsOn(this.boostSetting));
        this.autoLaunchSetting = this.add(new BooleanSetting("AutoLaunch", false).markVisibilityDependency());
        this.onlyElytraFlySetting = this.add(new BooleanSetting("OnlyElytraFly", true).dependsOn(this.autoLaunchSetting));
        this.onlyFirstSetting = this.add(new BooleanSetting("OnlyFirst", false).dependsOn(this.autoLaunchSetting));
        this.awaitSetting = this.add(new BooleanSetting("Await", false).dependsOn(this.autoLaunchSetting));
        NumberSetting<Float> delaySettingValue = new NumberSetting<>("Delay", 0.0F, 0.0F, 20.0F);
        delaySettingValue.withUnit("s");
        this.delaySetting = this.add(delaySettingValue.dependsOn(this.autoLaunchSetting));
        this.fastLaunchSetting = this.add(new BooleanSetting("FastLaunch", true).markVisibilityDependency());
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.MANUAL).dependsOn(this.fastLaunchSetting));
        this.swapSetting = this.add(new EnumSetting<>("Swap", FireworksModule.SwapChoice.NORMAL).dependsOn(this.fastLaunchSetting));
        Predicate<Object> ticksVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.MANUAL;
        this.ticksSetting = this.add(new NumberSetting<>("Ticks", 10, 3, 40, ticksVisibility).dependsOn(this.fastLaunchSetting));
        Predicate<Object> swapBackVisibility = ignored -> this.swapSetting.getValue() != SwapChoice.SILENT;
        this.swapBackSetting = this.add(new BooleanSetting("SwapBack", false, swapBackVisibility).dependsOn(this.fastLaunchSetting));
        this.autoPhaseSetting = this.add(new BooleanSetting("AutoPhase", false).dependsOn(this.fastLaunchSetting));
        this.rocketUseTimer = new ElapsedTimer();
        this.fakeRocketTimer = new ElapsedTimer();
        this.fakeRocketLifetimeTicks = 0;
        this.fakeRocketEntityId = -1;
        this.delaySetting.setBoundaryLabel("Auto", SettingLabelCondition.MIN);
        this.fastLaunchSetting.setChangeCallback(() -> {
            if (!this.isWorldUnavailable() && this.fastLaunchSetting.getValue()
                    && this.modeSetting.getValue() == FireworksModule.ModeChoice.TOGGLE) {
                this.rocketUseTimer.clear();
                ClientContext.inputOverrides.requestJump(true);
            }
        });
    }

    public static Vec3 getGrimBoost(float yaw, float pitch, float previousYaw, float previousPitch, Vec3 movement) {
        Vec3 lookDirection = Vec3.directionFromRotation(pitch, yaw);
        AABB movementBounds = getBoostMovementBounds(yaw, pitch, previousYaw, previousPitch, movement);
        return BoundingBoxUtils.clampToBounds(lookDirection.scale(0.8D), movementBounds);
    }

    public static AABB getBoostMovementBounds(float yaw, float pitch, float previousYaw, float previousPitch, Vec3 movement) {
        Vec3 currentLook = Vec3.directionFromRotation(pitch, yaw);
        Vec3 previousLook = Vec3.directionFromRotation(previousPitch, previousYaw);
        float yawFraction = ((yaw % 45.0F + 45.0F) % 45.0F) / 45.0F;
        double predictionScale = 1.54D;
        if (pitch < -35.0F) {
            predictionScale = 1.63D;
        } else if (pitch < -15.0F) {
            predictionScale = 1.62D;
        } else if (pitch < -10.0F) {
            predictionScale = 1.61D;
        }
        if ((double) yawFraction > 0.1D && (double) yawFraction < 0.9D) {
            predictionScale -= 0.05D;
        }
        double minimumX = Math.max(-predictionScale,
                (Math.min(-0.0D, currentLook.x) + Math.min(-0.0D, previousLook.x)) * predictionScale);
        double minimumY = Math.max(-predictionScale,
                (Math.min(-0.0D, currentLook.y) + Math.min(-0.0D, previousLook.y)) * predictionScale);
        double minimumZ = Math.max(-predictionScale,
                (Math.min(-0.0D, currentLook.z) + Math.min(-0.0D, previousLook.z)) * predictionScale);
        double maximumX = Math.min(predictionScale,
                (Math.max(0.0D, currentLook.x) + Math.max(0.0D, previousLook.x)) * predictionScale);
        double maximumY = Math.min(predictionScale,
                (Math.max(0.0D, currentLook.y) + Math.max(0.0D, previousLook.y)) * predictionScale);
        double maximumZ = Math.min(predictionScale,
                (Math.max(0.0D, currentLook.z) + Math.max(0.0D, previousLook.z)) * predictionScale);
        AABB bounds = new AABB(Math.min(0.0, minimumX - movement.x), Math.min(0.0, minimumY - movement.y),
                Math.min(0.0, minimumZ - movement.z), Math.max(0.0, maximumX - movement.x),
                Math.max(0.0, maximumY - movement.y), Math.max(0.0, maximumZ - movement.z));
        return bounds.contract(0.0, 0.0231D, 0.0);
    }

    public static void bindModules(ModuleRegistry modules) {
        autoArmorModule = modules.getModule(AutoArmorModule.class);
        autoMaceModule = modules.getModule(AutoMaceModule.class);
        rocketExtenderModule = modules.getModule(RocketExtenderModule.class);
        elytraFlyModule = modules.getModule(ElytraFlyModule.class);
    }

    @Override
    public void onToggle() {
        this.removeFakeRocket();
        ClientContext.tickRateController.releaseMultiplier(this);
        this.fastLaunchTicksRemaining = 0;
        this.fastLaunchWasActive = false;
        this.swapBackPending = false;
    }

    public boolean canFastLaunch(boolean requireAirborne) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        return !PlayerStateUtils.isWearingElytra(player) && (!requireAirborne || !player.onGround())
                && this.isToggled() && this.fastLaunchSetting.getValue()
                && InventoryUtils.findMenuSlot(Items.ELYTRA) != -1;
    }

    public float getAdditionalBoost(boolean fromRocketEntity) {
        if (!this.isToggled() || !this.boostSetting.getValue()) {
            return 0.0F;
        }
        if (!fromRocketEntity && !this.withElytraFlySetting.getValue()) {
            return 0.0F;
        }
        return this.isRocketBoostActive() ? this.boostFactorSetting.getValue() : 0.0F;
    }

    public boolean isFastLaunchActive() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player.onGround()) {
            return false;
        }
        FireworksModule.ModeChoice mode = this.modeSetting.getValue();
        return switch (mode) {
            case MANUAL -> this.fastLaunchTicksRemaining > 0;
            case TOGGLE -> this.fastLaunchSetting.getValue();
        };
    }

    public boolean isGrimBoostEnabled() {
        return this.isToggled() && this.boostSetting.getValue() && this.grimSetting.getValue();
    }

    @EventHandler(priority = 200)
    private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
        long fakeRocketLifetimeMillis = (long) this.fakeRocketLifetimeTicks * 50L;
        if (rocketExtenderModule.isToggled()) {
            fakeRocketLifetimeMillis = (long) (rocketExtenderModule.getTimeoutSeconds() * 1000.0F);
        }
        if (this.fakeRocketTimer.hasElapsed(fakeRocketLifetimeMillis)) {
            this.removeFakeRocket();
        }
        if (PlayerStateUtils.isFallFlying() && this.softLagSetting.getValue()
                && ClientContext.serverStateManager.hasElapsedSinceSetback(400L)) {
            ClientContext.tickRateController.requestMultiplier(this, 0.8F);
        } else {
            ClientContext.tickRateController.releaseMultiplier(this);
        }
        if (this.swapBackPending) {
            if (!InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3)) {
                SwapChoice swapMode = this.swapSetting.getValue();
                if (!swapMode.isSilent() && this.swappedElytraSlot != -1) {
                    (this.swapSetting.getValue()).swapElytra(this.swappedElytraSlot, 6);
                    autoArmorModule.skipNextArmorUpdate();
                    this.swappedElytraSlot = -1;
                }
                this.swapBackPending = false;
            }
            if (!PlayerStateUtils.isWearingElytra(ClientAccess.evernightMinecraft.player)
                    && (this.swapSetting.getValue()).isSilent()) {
                ClientAccess.evernightMinecraft.player.stopFallFlying();
                this.swapBackPending = false;
            }
        }
        if (this.fastLaunchSetting.getValue() && this.modeSetting.getValue() == ModeChoice.TOGGLE
                && ClientAccess.evernightMinecraft.player.onGround() && this.fastLaunchWasActive) {
            ClientContext.inputOverrides.requestJump(true);
        }
        if (this.isFastLaunchActive() && this.fastLaunchSetting.getValue()) {
            autoArmorModule.skipNextArmorUpdate();
            this.equipElytraAndStartFlight();
            if (this.modeSetting.getValue() == ModeChoice.MANUAL) {
                this.fastLaunchTicksRemaining--;
            }
            this.fastLaunchWasActive = true;
        } else if (this.fastLaunchWasActive) {
            if (this.swapBackSetting.getValue()) {
                autoArmorModule.skipNextArmorUpdate();
                this.swapBackPending = true;
            }
            this.fastLaunchWasActive = false;
            if (this.modeSetting.getValue() == ModeChoice.TOGGLE && this.fastLaunchSetting.getValue()
                    && ClientAccess.evernightMinecraft.player.onGround()) {
                autoArmorModule.skipNextArmorUpdate();
                this.swapBackPending = false;
            }
        }
        if (this.fastLaunchSetting.getValue() && this.autoPhaseSetting.getValue()) {
            double top = ClientAccess.evernightMinecraft.player.getBoundingBox().maxY;
            if (Math.floor(top) == Math.floor(top + 0.01D) && !ClientAccess.evernightMinecraft.player.onGround()) {
                this.ceilingFlightTicks++;
            } else {
                this.ceilingFlightTicks = 0;
            }
            this.tryAutoPhase();
            if (!ClientAccess.evernightMinecraft.player.isFallFlying() && EntityUtils.hasFirework(ClientAccess.evernightMinecraft.player)
                    && ClientAccess.evernightMinecraft.player.getXRot() < 0.0F
                    && KeyUtils.isPhysicallyDown(ClientAccess.evernightMinecraft.options.keyJump)) {
                PacketSender.sendStartFallFlying();
                ClientAccess.evernightMinecraft.player.startFallFlying();
            }
        }
        boolean toggleLaunchActive = this.modeSetting.getValue() == ModeChoice.TOGGLE && this.isFastLaunchActive();
        boolean fallFlyingForLaunch = PlayerStateUtils.isFallFlying();
        if (elytraFlyModule.isToggled() && elytraFlyModule.isDurabilityBypassActive()
                && elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.NEWGRIM) {
            fallFlyingForLaunch = true;
        }
        if (!fallFlyingForLaunch || ClientAccess.evernightMinecraft.player.onGround()) {
            this.rocketUseTimer.clear();
        }
        if (!this.autoLaunchSetting.getValue()) {
            return;
        }
        if (!elytraFlyModule.isToggled() && this.onlyElytraFlySetting.getValue() && !toggleLaunchActive) {
            return;
        }
        if (elytraFlyModule.isToggled() && elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE) {
            return;
        }
        double delaySeconds = this.delaySetting.getValue();
        boolean awaitRocket = !this.rocketUseTimer.hasElapsed(delaySeconds, TimeUnit.SECONDS)
                || rocketExtenderModule.isToggled() && rocketExtenderModule.trackedRocketId != -1 && rocketExtenderModule.rangeAnchor != null;
        if (this.isRocketBoostActive()) {
            awaitRocket = true;
        }
        if (awaitRocket) {
            return;
        }
        if (!PlayerStateUtils.isFallFlying()) {
            this.rocketLaunchedDuringFlight = false;
        }
        if (this.rocketLaunchedDuringFlight && this.onlyFirstSetting.getValue()) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player.onGround()) {
            return;
        }
        if (elytraFlyModule.isToggled() && elytraFlyModule.modeSetting.getValue() == ElytraFlyModule.ModeChoice.NEWGRIM
                && !(elytraFlyModule.infDurabilitySetting.getValue()).isDisabled()
                && !RotationManager.is2b2tMode()) {
            this.temporarilyEquipElytra();
        }
        this.tryUseRocket();
    }

    @EventHandler
    private void onSoundEnginePlayEvent(SoundEnginePlayEvent event) {
        if (this.fastLaunchTicksRemaining <= 0 || event.getSoundInstance() == null) {
            return;
        }
        Identifier identifier = event.getSoundInstance().getIdentifier();
        if (identifier != null && identifier.toString().contains("item.armor.equip")) {
            event.cancel();
        }
    }

    @EventHandler(priority = -100)
    private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
        float yaw = ClientAccess.evernightMinecraft.player.getYRot();
        float pitch = ClientAccess.evernightMinecraft.player.getXRot();
        RotationManager rotationManager = ClientContext.rotationManager;
        if (rotationManager.getRequest() != null) {
            yaw = ClientContext.rotationManager.getRequest().angles()[0];
            pitch = ClientContext.rotationManager.getRequest().angles()[1];
        }
        if (!autoMaceModule.shouldSuppressGrimBoost() && PlayerStateUtils.isFallFlying() && this.boostSetting.getValue()
                && this.grimSetting.getValue()) {
            if (EntityUtils.hasFirework(ClientAccess.evernightMinecraft.player)) {
                float[] packetRotations = ClientContext.playerStateTracker.getPacketRotations();
                float previousYaw = packetRotations[0];
                float previousPitch = packetRotations[1];
                Vec3 movement = event.getOriginalVelocity();
                Vec3 boostMovement = getGrimBoost(yaw, pitch, previousYaw, previousPitch, movement);
                Vec3 currentMovement = event.getOriginalVelocity();
                Vec3 boostedMovement = currentMovement.add(boostMovement);
                event.setVelocity(boostedMovement);
            }
        }
        if (PlayerStateUtils.isFallFlying() && this.awaitSetting.getValue()
                && this.autoLaunchSetting.getValue()) {
            if (!this.isRocketBoostActive()) {
                Vec3 pausedMovement = new Vec3(0.0, 0.0, 0.0);
                event.setVelocity(pausedMovement);
            }
        }
    }

    @EventHandler
    private void onMultiPlayerGameModeUseItemEvent(MultiPlayerGameModeUseItemEvent event) {
        if (!ClientAccess.evernightMinecraft.player.isHolding(Items.FIREWORK_ROCKET)) {
            return;
        }
        Boolean fakeRocketEnabled = this.fakeRocketSetting.getValue();
        if (fakeRocketEnabled && PlayerStateUtils.isFallFlying()) {
            this.removeFakeRocket();
            this.fakeRocketEntityId = -84751;
            LocalPlayer playerForRocketStack = ClientAccess.evernightMinecraft.player;
            InteractionHand hand = event.getHand();
            ItemStack rocketStack = playerForRocketStack.getItemInHand(hand);
            FireworkRocketEntity fakeRocket = new FireworkRocketEntity(ClientAccess.evernightMinecraft.level,
                    rocketStack, ClientAccess.evernightMinecraft.player);
            int entityId = this.fakeRocketEntityId;
            fakeRocket.setId(entityId);
            int flightDuration = 1;
            DataComponentType<Fireworks> fireworksType = DataComponents.FIREWORKS;
            Fireworks fireworksComponent = rocketStack.get(fireworksType);
            if (fireworksComponent != null) {
                flightDuration += fireworksComponent.flightDuration();
            }
            int lifetimeTicks = 10 * flightDuration;
            lifetimeTicks += ThreadLocalRandom.current().nextInt(6);
            this.fakeRocketLifetimeTicks = lifetimeTicks + ThreadLocalRandom.current().nextInt(7);
            ClientAccess.evernightMinecraft.level.addEntity(fakeRocket);
            this.fakeRocketTimer.reset();
            event.cancel();
        }
        Boolean fastLaunchEnabled = this.fastLaunchSetting.getValue();
        if (fastLaunchEnabled && PlayerStateUtils.isLocalWearingElytra() && !PlayerStateUtils.isFallFlying()) {
            if (!ClientAccess.evernightMinecraft.player.onGround()) {
                PacketSender.sendStartFallFlying();
                ClientAccess.evernightMinecraft.player.startFallFlying();
            }
        }
        if (this.canFastLaunch(true)) {
            this.equipElytraAndStartFlight();
            Integer launchTicks = this.ticksSetting.getValue();
            this.fastLaunchTicksRemaining = launchTicks;
            this.resetRocketAge = true;
        } else if (this.fastLaunchTicksRemaining > 0) {
            Integer refreshedLaunchTicks = this.ticksSetting.getValue();
            this.fastLaunchTicksRemaining = refreshedLaunchTicks;
        }
    }

    @EventHandler
    private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
        if (this.antiWasteSetting.getValue()
                && ClientAccess.evernightMinecraft.player.getItemInHand(event.getHand()).is(Items.FIREWORK_ROCKET)) {
            ClientAccess.evernightMinecraft.gameMode.useItem(ClientAccess.evernightMinecraft.player, event.getHand());
            ClientAccess.evernightMinecraft.player.swing(event.getHand());
            event.cancel();
        }
    }

    private void removeFakeRocket() {
        if (this.fakeRocketEntityId != -1) {
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            if (level != null) {
                level.removeEntity(this.fakeRocketEntityId, RemovalReason.KILLED);
            }
            this.fakeRocketEntityId = -1;
        }
    }

    private void equipElytraAndStartFlight() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player.onGround()) {
            return;
        }
        SwapChoice swapMode = this.swapSetting.getValue();
        if (!swapMode.isSilent()) {
            if (PlayerStateUtils.isLocalWearingElytra()) {
                if (!PlayerStateUtils.isFallFlying()) {
                    PacketSender.sendStartFallFlying();
                    player.startFallFlying();
                }
                return;
            }
            int slot = InventoryUtils.findMenuSlot(Items.ELYTRA);
            if (slot != -1 && !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 4)) {
                autoArmorModule.skipNextArmorUpdate();
                swapMode.swapElytra(slot, 6);
                if (!PlayerStateUtils.isFallFlying()) {
                    PacketSender.sendStartFallFlying();
                    player.startFallFlying();
                }
                this.swappedElytraSlot = slot;
            }
            return;
        }
        int slot = InventoryUtils.findHotbarSlot(Items.ELYTRA);
        boolean inventorySwapRequired = slot == -1;
        if (inventorySwapRequired) {
            slot = InventoryUtils.findMenuSlot(Items.ELYTRA);
        }
        if (slot == -1) {
            return;
        }
        int originalMenuSlot = slot;
        if (inventorySwapRequired) {
            slot = 0;
            InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP);
        }
        InventoryUtils.clickSlot(6, slot, ClickType.SWAP);
        try {
            PacketSender.sendStartFallFlying();
            player.startFallFlying();
        } finally {
            InventoryUtils.clickSlot(6, slot, ClickType.SWAP);
            if (inventorySwapRequired) {
                InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP);
            }
        }
    }

    private void tryAutoPhase() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (!PlayerStateUtils.isFallFlying() || !EntityUtils.hasFirework(player)) {
            return;
        }
        if (RaycastUtils.raycastWithEntityTrace(player) instanceof EntityHitResult hit
                && hit.getEntity().getType() == EntityType.END_CRYSTAL) {
            return;
        }
        int selectedSlot = InventoryUtils.getSelectedSlot();
        InventoryUtils.SwapSlot pearlSlot = InventoryUtils.findSwapSlot(Items.ENDER_PEARL, true);
        Vec3 lookDirection = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        AABB projectedBounds = player.getBoundingBox().move(lookDirection.scale(0.1D));
        boolean blockedAhead = !ClientAccess.evernightMinecraft.level.noBlockCollision(player, projectedBounds)
                && this.ceilingFlightTicks >= 2;
        boolean pearlTarget = !player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())
                && ClientAccess.evernightMinecraft.hitResult instanceof BlockHitResult hit
                && WorldUtils.hasCollision(hit.getBlockPos());
        if (this.autoPhaseSetting.getValue() && pearlSlot.isFound() && blockedAhead && pearlTarget) {
            InventoryUtils.swapTo(pearlSlot);
            try {
                ClientAccess.evernightMinecraft.gameMode.useItem(player, pearlSlot.hand());
                PacketSender.sendSwing(pearlSlot.hand());
            } finally {
                InventoryUtils.swapBack(selectedSlot, pearlSlot);
            }
        }
    }

    public void tryUseRocket() {
        if (!this.autoLaunchSetting.getValue()
                || !PlayerStateUtils.isFallFlying() && !temporaryElytraSwapActive.get()
                || ClientAccess.evernightMinecraft.player.isUsingItem()) {
            return;
        }
        int selectedSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(Items.FIREWORK_ROCKET, true);
        if (!rocketSlot.isFound()) {
            return;
        }
        InventoryUtils.swapTo(rocketSlot);
        try {
            ClientAccess.evernightMinecraft.gameMode.useItem(ClientAccess.evernightMinecraft.player, rocketSlot.hand());
            ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
        } finally {
            InventoryUtils.swapBack(selectedSlot, rocketSlot);
        }
        rocketExtenderModule.launchPosition = ClientAccess.evernightMinecraft.player.position();
        this.rocketUseTimer.reset();
        this.rocketLaunchedDuringFlight = true;
    }

    public boolean isRocketBoostActive() {
        return !this.rocketUseTimer.hasElapsed(500L) || EntityUtils.hasFirework(ClientAccess.evernightMinecraft.player);
    }

    private void temporarilyEquipElytra() {
        int elytraSlot = InventoryUtils.findHotbarSlot(Items.ELYTRA);
        if (elytraSlot == -1 || !temporaryElytraSwapActive.compareAndSet(false, true)) {
            return;
        }
        InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP);
        PacketSender.sendStartFallFlying();
        ClientAccess.evernightMinecraft.player.startFallFlying();
        ClientContext.taskScheduler.scheduleRotationTask(() -> {
            try {
                InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP);
            } finally {
                temporaryElytraSwapActive.set(false);
            }
        }, 2);
    }

    public enum ModeChoice implements NamedEntry {
        MANUAL("Manual"),
        TOGGLE("Toggle");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    public enum SwapChoice implements NamedEntry {
        NORMAL("Normal"),
        ALT("Alt"),
        SILENT("Silent");
        private final String displayName;

        SwapChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }

        public boolean isSilent() {
            return this == SILENT;
        }

        public void swapElytra(int elytraSlot, int chestMenuSlot) {
            if (this == NORMAL) {
                InventoryUtils.swapSlotsWithPickup(elytraSlot, chestMenuSlot);
            } else if (this == ALT) {
                InventoryUtils.swapMenuSlots(elytraSlot, chestMenuSlot);
            }
        }
    }
}