package evernight.client.modules.combat.AutoMace;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule.InfDurabilityChoice;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule.ModeChoice;
import evernight.client.modules.movement.Fireworks.FireworksModule;
import evernight.client.modules.movement.NoSlow.NoSlowModule;
import evernight.client.settings.options.EntitySortChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.DamageUtils;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.combat.TargetSelector;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.ArmorSlot;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.math.Vec3Utils;
import evernight.client.utils.movement.ControlElytraFlyHandler;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.InteractPacketUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.AuraAttackEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.modules.combat.Aura.AuraModule;
import evernight.client.modules.combat.AutoArmor.AutoArmorModule;
import evernight.client.modules.combat.Criticals.CriticalsModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public final class AutoMaceModule extends Module {
    private static AuraModule aura;
    private static ElytraFlyModule elytraFly;
    private static AutoArmorModule autoArmor;
    private static NoSlowModule noSlow;
    private static FireworksModule fireworks;

    static {
        aura = ClientContext.getModule(AuraModule.class);
        elytraFly = ClientContext.getModule(ElytraFlyModule.class);
        autoArmor = ClientContext.getModule(AutoArmorModule.class);
        noSlow = ClientContext.getModule(NoSlowModule.class);
        fireworks = ClientContext.getModule(FireworksModule.class);
    }

    public final Setting<Boolean> nakedsSetting;
    final Setting<Color> colorSetting;
    private final Setting<EntitySortChoice> sortingSetting;
    private final Setting<Float> rangeSetting;
    private final Setting<Float> heightSetting;
    private final Setting<Boolean> smartSetting;
    private final Setting<Float> angleSetting;
    private final Setting<Boolean> autoBoostSetting;
    private final Setting<Boolean> renderSetting;
    private final Setting<Boolean> targetsSetting;
    private final Setting<Boolean> playersSetting;
    private final Setting<Boolean> animalsSetting;
    private final Setting<Boolean> hostilesSetting;
    private final Setting<Boolean> neutralsSetting;
    private final Setting<Boolean> spoofSetting;
    private final Setting<MaceSpoofMode> spoofModeSetting;
    private final Setting<Double> spoofHeightSetting;
    private final Setting<Integer> spoofDelaySetting;
    private final Setting<Boolean> spoofGroundSetting;
    private final Setting<Boolean> spoofAirSetting;
    private final Setting<Boolean> spoofIgnoreCrystalSetting;
    private final Setting<Boolean> spoofWithCriticalsSetting;
    private final ElapsedTimer spoofTimer = new ElapsedTimer();
    private final AutoMaceTargetRenderer targetRenderer;
    private final AutoMaceStrikePlanner strikePlanner;
    private final ElapsedTimer boostTimer;
    private final ElapsedTimer strikeTimer;
    private final ElapsedTimer recentFlightTimer;
    private boolean spoofSendingAttack;
    private float verticalDisplacement;
    private int savedChestplateSlot;
    private int pendingChestSwapSlot;
    private boolean striking;
    private boolean maceAvailable;
    private Entity target;

    public AutoMaceModule() {
        super("AutoMace", ModuleCategory.COMBAT);
        this.sortingSetting = this.add(new EnumSetting<>("Sorting", EntitySortChoice.CROSSHAIR));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 64.0F, 16.0F, 256.0F).withUnit("m"));
        this.smartSetting = this.add(new BooleanSetting("Smart", true));
        this.heightSetting = this.add(new NumberSetting<>("Height", 15.0F, 5.0F, 30.0F).withUnit("m"));
        this.angleSetting = this.add(new NumberSetting<>("Angle", 75.0F, 45.0F, 90.0F).withUnit("°"));
        this.autoBoostSetting = this.add(new BooleanSetting("AutoBoost", true));
        this.renderSetting = this.add(new BooleanSetting("Render", false).markVisibilityDependency());
        this.colorSetting = this.add(new ColorSetting("Color", new Color(-4351489, true)).dependsOn(this.renderSetting));
        this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
        this.playersSetting = this.add(new BooleanSetting("Players", true).dependsOn(this.targetsSetting));
        this.nakedsSetting = this.add(new BooleanSetting("Nakeds", false).dependsOn(this.targetsSetting, this.playersSetting));
        this.animalsSetting = this.add(new BooleanSetting("Animals", false).dependsOn(this.targetsSetting));
        this.hostilesSetting = this.add(new BooleanSetting("Hostiles", false).dependsOn(this.targetsSetting));
        this.neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
        this.spoofSetting = this.add(new BooleanSetting("Spoof", false).markVisibilityDependency());
        this.spoofModeSetting = this.add(new EnumSetting<>("SpoofMode", MaceSpoofMode.HOLD).dependsOn(this.spoofSetting));
        this.spoofHeightSetting = this.add(new NumberSetting<>("SpoofHeight", 25.0D, 0.0D, 2000.0D).dependsOn(this.spoofSetting));
        this.spoofDelaySetting = this.add(new NumberSetting<>("SpoofDelay", 500, 0, 5000).withUnit("ms").dependsOn(this.spoofSetting));
        this.spoofGroundSetting = this.add(new BooleanSetting("SpoofGround", true).dependsOn(this.spoofSetting));
        this.spoofAirSetting = this.add(new BooleanSetting("SpoofAir", true).dependsOn(this.spoofSetting));
        this.spoofIgnoreCrystalSetting = this.add(new BooleanSetting("SpoofIgnoreCrystal", true).dependsOn(this.spoofSetting));
        this.spoofWithCriticalsSetting = this.add(new BooleanSetting("SpoofWithCriticals", true).dependsOn(this.spoofSetting));
        this.targetRenderer = new AutoMaceTargetRenderer(this);
        this.strikePlanner = new AutoMaceStrikePlanner();
        this.boostTimer = new ElapsedTimer();
        this.strikeTimer = new ElapsedTimer();
        this.recentFlightTimer = new ElapsedTimer();
        this.rangeSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX);
    }

    public static void bindReferencedModules(ModuleRegistry modules) {
        aura = modules.getModule(AuraModule.class);
        elytraFly = modules.getModule(ElytraFlyModule.class);
        autoArmor = modules.getModule(AutoArmorModule.class);
        noSlow = modules.getModule(NoSlowModule.class);
        fireworks = modules.getModule(FireworksModule.class);
    }

    private void restoreChestSwap() {
        if (this.pendingChestSwapSlot != -1) {
            int swapSlot = this.pendingChestSwapSlot;
            ArmorSlot chestSlotType = ArmorSlot.CHESTPLATE;
            int chestContainerSlot = chestSlotType.getContainerSlot();
            InventoryUtils.swapMenuSlots(swapSlot, chestContainerSlot);
            if (PlayerStateUtils.isLocalWearingElytra() && !ClientAccess.evernightMinecraft.player.onGround()) {
                this.startGliding();
            }
            this.pendingChestSwapSlot = -1;
        }
    }

    private boolean hasApproachPath(Entity candidate) {
        Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
        Vec3 targetPosition = candidate.position();
        Float rangeValue = this.rangeSetting.getValue();
        if (!eyePosition.closerThan(targetPosition, rangeValue.floatValue())) {
            return false;
        }
        AABB targetBounds = candidate.getBoundingBox().inflate(0.5D);
        int[] solidBlockCount = new int[]{0};
        Boolean blocked = BlockGetter.traverseBlocks(eyePosition, targetPosition, null,
                (ignoredEntity, position) -> {
                    BlockPos statePosition = position;
                    BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(statePosition);
                    BlockPos intersectionPosition = position;
                    if (targetBounds.intersects(intersectionPosition)) {
                        return null;
                    }
                    VoxelShape collisionShape = state.getCollisionShape(ClientAccess.evernightMinecraft.level, position);
                    if (!collisionShape.isEmpty()) {
                        if (++solidBlockCount[0] > 1) {
                            return true;
                        }
                        BlockPos airSpacePosition = position;
                        if (this.hasNearbyAirSpace(airSpacePosition)) {
                            return null;
                        }
                        return true;
                    } else {
                        return null;
                    }
                }, unused -> false);
        return !blocked;
    }

    private boolean isTargetInvalid(Entity candidate) {
        if (!candidate.isAlive()) {
            return true;
        }
        Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
        Vec3 targetPosition = this.target.position();
        Float rangeValue = this.rangeSetting.getValue();
        if (!eyePosition.closerThan(targetPosition, rangeValue.floatValue())) {
            return true;
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        int entityId = candidate.getId();
        if (level.getEntity(entityId) == null) {
            return true;
        }
        return ClientContext.socialRegistry.isFriendEntity(candidate);
    }

    public float[] getFlightRotation() {
        if (this.target == null) {
            var player = ClientAccess.evernightMinecraft.player;
            return new float[]{player.getYRot(), player.getXRot()};
        }
        Vec3 targetPosition = this.target.position();
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 eyePosition = player.getEyePosition();
        float horizontalDistance = Vec3Utils.horizontalDistance(targetPosition, eyePosition);
        boolean reverseYaw = horizontalDistance < 12.0F;
        Vec3 offsetPosition = this.target.position();
        double targetHeight = this.target.getBbHeight();
        Vec3 targetTop = offsetPosition.add(0.0, targetHeight, 0.0);
        float[] targetRotation = RotationManager.getRotationsToPosition(targetTop);
        if (this.striking) {
            float[] strikeRotation = this.strikePlanner.rotation();
            return strikeRotation != null ? strikeRotation : targetRotation;
        }
        float[] flightRotation = new float[2];
        float targetYaw = targetRotation[0];
        int yawOffset;
        if (reverseYaw) {
            yawOffset = 180;
        } else {
            yawOffset = 0;
        }
        flightRotation[0] = Mth.wrapDegrees(targetYaw + yawOffset);
        Float angleValue = this.angleSetting.getValue();
        flightRotation[1] = -angleValue;
        return flightRotation;
    }

    private void refreshMaceAvailability() {
        Predicate<ItemStack> macePredicate = ItemUtils::isMace;
        InventoryUtils.SwapSlot maceSlot = InventoryUtils.findSwapSlot(macePredicate, true);
        this.maceAvailable = maceSlot.isFound();
    }

    private void updateFlightRotation() {
        if (!this.isFlightControlActive()) {
            this.strikePlanner.reset();
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        double heightAboveTarget = player.getY() - this.target.getBoundingBox().maxY;
        double horizontalDistance = Vec3Utils.horizontalDistance(player.position(), this.target.position());
        if (heightAboveTarget > this.heightSetting.getValue() || this.hasOverheadCollision()) {
            this.striking = true;
        }
        boolean controlMode = elytraFly.isToggled()
                && elytraFly.modeSetting.getValue() == ElytraFlyModule.ModeChoice.CONTROL;
        if (this.striking && this.smartSetting.getValue()
                && (PlayerStateUtils.isLocalWearingElytra() || PlayerStateUtils.isFallFlying())) {
            double range = aura.getEffectiveRange(aura.rangeSetting);
            if (controlMode) {
                this.strikePlanner.updateControl(this.target, range,
                        elytraFly.flightSpeedSetting.getValue() + fireworks.getAdditionalBoost(false),
                        elytraFly.vSpeedSetting.getValue(), elytraFly.glideSetting.getValue());
            } else {
                double boostBudget = Double.POSITIVE_INFINITY;
                if (fireworks.isRocketBoostActive()) {
                    Entity movingTarget = this.target.isPassenger() ? this.target.getVehicle() : this.target;
                    boostBudget = MovementPhysics.getHorizontalSpeedKmh(movingTarget) <= 60.0D ? 10.0D : 0.0D;
                }
                this.strikePlanner.updateNormal(this.target, range, true, boostBudget);
            }
        } else {
            this.strikePlanner.reset();
        }
        if (controlMode && elytraFly.getActiveMode() instanceof ControlElytraFlyHandler control) {
            int verticalInput;
            if (this.strikePlanner.isActive()) {
                verticalInput = this.strikePlanner.shouldDescend() ? -1 : 0;
            } else if (this.striking) {
                verticalInput = horizontalDistance < elytraFly.flightSpeedSetting.getValue() * 3.0F ? -1 : 0;
            } else {
                verticalInput = 1;
            }
            control.setVerticalInputOverride(verticalInput);
        }
        RotationManager rotationManager = ClientContext.rotationManager;
        rotationManager.forceRotationOverride();
        rotationManager.requestRotation(this.getFlightRotation(), 2000, true);
    }

    private void boostWithRocket() {
        if (this.target == null || !PlayerStateUtils.isFallFlying()) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player.onGround()) {
            return;
        }
        if (EntityUtils.hasFirework(ClientAccess.evernightMinecraft.player)) {
            return;
        }
        if (!this.boostTimer.hasElapsed(400L)) {
            return;
        }
        int previousSlot = InventoryUtils.getSelectedSlot();
        Item rocketItem = Items.FIREWORK_ROCKET;
        InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(rocketItem, true);
        if (!rocketSlot.isFound()) {
            return;
        }
        InventoryUtils.swapTo(rocketSlot);
        MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
        LocalPlayer usePlayer = ClientAccess.evernightMinecraft.player;
        InteractionHand useHand = rocketSlot.hand();
        gameMode.useItem(usePlayer, useHand);
        SwingInteractionManager swingManager = ClientContext.swingManager;
        InteractionHand swingHand = rocketSlot.hand();
        SwingAction swingAction = SwingAction.INTERACT;
        swingManager.swing(swingHand, swingAction);
        InventoryUtils.swapBack(previousSlot, rocketSlot);
        this.boostTimer.reset();
    }

    private void selectTarget() {
        Function<Entity, Integer> priority = this::getTargetPriority;
        Comparator<Entity> priorityComparator = Comparator.comparing(priority);
        Comparator<Entity> sortingComparator = (this.sortingSetting.getValue()).getComparator();
        Comparator<Entity> comparator = priorityComparator.thenComparing(sortingComparator);
        TargetSelector selector = new TargetSelector();
        Setting<Float> rangeSettingSnapshot = this.rangeSetting;
        float range;
        if (rangeSettingSnapshot.isAtMaximum()) {
            range = 2048.0F;
        } else {
            range = this.rangeSetting.getValue();
        }
        selector = selector.range(range);
        selector = selector.withPlayers(this.playersSetting.getValue());
        boolean ignoreNakeds = !this.nakedsSetting.getValue();
        selector = selector.ignoreNakeds(ignoreNakeds).filter(this::hasApproachPath);
        selector = selector.withAnimals(this.animalsSetting.getValue());
        selector = selector.withNeutrals(this.neutralsSetting.getValue());
        selector = selector.withHostiles(this.hostilesSetting.getValue());
        selector = selector.sortBy(comparator);
        this.target = selector.select();
    }

    private int getTargetPriority(Entity candidate) {
        if (candidate instanceof Player player) {
            return ClientContext.socialRegistry.isEnemyPlayer(player) ? 0 : 1;
        }
        return 1;
    }

    private boolean hasOverheadCollision() {
        int solidBlocks = 0;
        AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        bounds = bounds.setMinY(bounds.maxY);
        bounds = bounds.inflate(1.0D, 0.0, 1.0D);
        Iterator<BlockPos> positions = BlockPos.betweenClosed(bounds).iterator();
        while (positions.hasNext()) {
            BlockPos position = positions.next();
            position = position.above();
            VoxelShape collisionShape = ClientAccess.evernightMinecraft.level.getBlockState(position).getCollisionShape(ClientAccess.evernightMinecraft.level, position);
            if (!collisionShape.isEmpty()) {
                solidBlocks++;
            }
        }
        return solidBlocks > 3;
    }

    @Override
    public String getInfo() {
        if (this.target != null && this.target instanceof Player) {
            Object[] infoArguments = new Object[2];
            Entity nameTarget = this.target;
            infoArguments[0] = nameTarget.getName().getString();
            double distance = WorldUtils.getEntityDistance(this.target, false);
            infoArguments[1] = distance;
            return "%s, %.1f".formatted(infoArguments);
        } else {
            return null;
        }
    }

    @Override
    public void onEnable() {
        ClientAccess.eventBus.subscribe(this.targetRenderer);
        if (!this.isWorldUnavailable()) {
            this.selectTarget();
            this.targetRenderer.reset();
            this.strikePlanner.reset();
            this.strikeTimer.clear();
            this.savedChestplateSlot = this.pendingChestSwapSlot = -1;
            this.striking = false;
        }
    }

    @Override
    public void onDisable() {
        ClientAccess.eventBus.unsubscribe(this.targetRenderer);
        if (this.isWorldUnavailable()) {
            return;
        }
        boolean savedSlotInvalid = false;
        if (this.savedChestplateSlot != -1) {
            ItemStack savedStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(this.savedChestplateSlot);
            savedSlotInvalid = !this.isChestArmor(savedStack);
        }
        if (!autoArmor.isToggled() && !savedSlotInvalid) {
            if (this.savedChestplateSlot != -1) {
                this.pendingChestSwapSlot = this.savedChestplateSlot;
                this.savedChestplateSlot = -1;
            }
            this.restoreChestSwap();
        } else {
            this.savedChestplateSlot = this.pendingChestSwapSlot = -1;
        }
    }

    private void refreshTarget() {
        if (this.target != null) {
            Entity targetSnapshot = this.target;
            if (this.isTargetInvalid(targetSnapshot)) {
                this.target = null;
            }
        }
        if (this.target == null) {
            this.selectTarget();
        }
        if (PlayerStateUtils.isInWater(ClientAccess.evernightMinecraft.player)) {
            this.target = null;
        }
    }

    @EventHandler(priority = -100)
    public void steerFlightOnTick(LocalPlayerPreTickEvent event) {
        this.updateFlightRotation();
    }

    private void startGliding() {
        ClientAccess.evernightMinecraft.player.startFallFlying();
        PacketSender.sendStartFallFlying();
    }

    private boolean isChestArmor(ItemStack stack) {
        TagKey chestArmorTag = ItemTags.CHEST_ARMOR;
        return stack.is(chestArmorTag);
    }

    @EventHandler(priority = -100)
    public void finishStrikeStage(AuraAttackEvent event) {
        if (event.getStage() != EventStage.Pre) {
            if (this.striking && this.maceAvailable && this.target != null) {
                this.striking = false;
                this.strikeTimer.reset();
            }
        }
    }

    @EventHandler
    public void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        this.striking = false;
        this.target = null;
        this.strikeTimer.clear();
        this.strikePlanner.reset();
        this.targetRenderer.reset();
    }

    @EventHandler
    public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        double previousY = ClientAccess.evernightMinecraft.player.yo;
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        double verticalDelta = previousY - player.getY();
        this.verticalDisplacement = (float) Math.abs(verticalDelta);
        if (PlayerStateUtils.isFallFlying()) {
            this.recentFlightTimer.reset();
        }
    }

    @EventHandler(priority = 100)
    public void prepareMaceTick(LocalPlayerPreTickEvent event) {
        this.refreshMaceAvailability();
        this.refreshTarget();
        boolean infiniteDurabilityMode = false;
        if (elytraFly.isToggled()) {
            Setting<InfDurabilityChoice> durabilitySettingSnapshot = elytraFly.infDurabilitySetting;
            infiniteDurabilityMode = !(durabilitySettingSnapshot.getValue()).isDisabled();
        }
        if (infiniteDurabilityMode || this.target != null && this.maceAvailable) {
            if (!PlayerStateUtils.isLocalWearingElytra() && !this.striking && this.pendingChestSwapSlot == -1
                    && !infiniteDurabilityMode) {
                autoArmor.skipNextArmorUpdate();
                this.equipElytra();
            } else {
                if (this.renderSetting.getValue()) {
                    this.targetRenderer.addTargetMarker(this.target);
                }
                if (this.autoBoostSetting.getValue()) {
                    this.boostWithRocket();
                }
                if (this.target != null) {
                    autoArmor.skipNextArmorUpdate();
                    double targetTopY = this.target.getBoundingBox().maxY;
                    double heightAboveTarget = ClientAccess.evernightMinecraft.player.getY() - targetTopY;
                    if (!this.striking) {
                        this.restoreChestSwap();
                        if (!PlayerStateUtils.isFallFlying()) {
                            if (PlayerStateUtils.isWearingElytra(ClientAccess.evernightMinecraft.player)) {
                                if (!EntityUtils.hasCollisionBelow(ClientAccess.evernightMinecraft.player)) {
                                    PacketSender.sendStartFallFlying();
                                    ClientAccess.evernightMinecraft.player.startFallFlying();
                                }
                            }
                        }
                        if (!PlayerStateUtils.isFallFlying()) {
                            if (ClientAccess.evernightMinecraft.player.onGround()) {
                                if (!this.strikeTimer.hasElapsed(500L)) {
                                    ClientContext.inputOverrides.requestJump(true);
                                }
                            }
                        }
                    } else {
                        if (ClientAccess.evernightMinecraft.player.onGround()) {
                            this.striking = false;
                            this.strikeTimer.reset();
                            return;
                        }
                        double playerY = ClientAccess.evernightMinecraft.player.getY();
                        if (playerY < this.target.getY() - this.target.getBbHeight()) {
                            this.striking = false;
                        }
                        Object auraRangeValue = aura.rangeSetting.getValue();
                        Setting<Float> auraWallRangeSetting = aura.wallRangeSetting;
                        boolean visibleTarget;
                        if (Objects.equals(auraRangeValue, auraWallRangeSetting.getValue())) {
                            visibleTarget = RaycastUtils.hasVisibleEntitySample(this.target);
                        } else {
                            visibleTarget = true;
                        }
                        AuraModule auraSnapshot = aura;
                        Setting<Float> effectiveRangeSetting = visibleTarget ? aura.rangeSetting : aura.wallRangeSetting;
                        float attackRange = auraSnapshot.getEffectiveRange(effectiveRangeSetting);
                        float verticalSpeed = this.verticalDisplacement;
                        float effectiveRange = attackRange + verticalSpeed * 2.0F;
                        if (noSlow.isGrimStrictInventoryEnabled()) {
                            effectiveRange += this.verticalDisplacement;
                        }
                        Setting<ModeChoice> flightModeSettingSnapshot = elytraFly.modeSetting;
                        if (flightModeSettingSnapshot.getValue() == ElytraFlyModule.ModeChoice.CONTROL) {
                            Setting<Float> verticalSpeedSettingSnapshot = elytraFly.vSpeedSetting;
                            Float verticalSpeedValue = verticalSpeedSettingSnapshot.getValue();
                            effectiveRange += verticalSpeedValue;
                        }
                        boolean withinAttackRange = this.strikePlanner.isActive()
                                ? this.strikePlanner.hitTicks() <= (noSlow.isGrimStrictInventoryEnabled() ? 1 : 0)
                                : heightAboveTarget <= effectiveRange;
                        if (withinAttackRange && this.pendingChestSwapSlot == -1) {
                            if (PlayerStateUtils.isLocalWearingElytra()) {
                                int chestplateSlot = this.findChestArmorOrEmptySlot();
                                if (chestplateSlot != -1) {
                                    this.pendingChestSwapSlot = InventoryUtils.toMenuSlot(chestplateSlot);
                                    int chestSwapSlot = this.pendingChestSwapSlot;
                                    ArmorSlot chestSlotType = ArmorSlot.CHESTPLATE;
                                    int chestContainerSlot = chestSlotType.getContainerSlot();
                                    InventoryUtils.swapMenuSlots(chestSwapSlot, chestContainerSlot);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            this.restoreChestSwap();
        }
    }

    private boolean hasNearbyAirSpace(BlockPos position) {
        int airBlocks = 0;
        AABB bounds = new AABB(position);
        bounds = bounds.inflate(0.5D, -0.5D, 0.5D);
        Iterator<BlockPos> positions = BlockPos.betweenClosed(bounds).iterator();
        while (positions.hasNext()) {
            BlockPos neighbor = positions.next();
            VoxelShape collisionShape = ClientAccess.evernightMinecraft.level.getBlockState(neighbor).getCollisionShape(ClientAccess.evernightMinecraft.level, neighbor);
            if (collisionShape.isEmpty()) {
                airBlocks++;
            }
        }
        return airBlocks > 2;
    }

    private int findChestArmorOrEmptySlot() {
        int slot = InventoryUtils.findInventorySlot(this::isChestArmor, false);
        if (slot == -1) {
            slot = InventoryUtils.findInventorySlot(ItemStack::isEmpty, false);
        }
        return slot;
    }

    public boolean isStrikingTarget() {
        return this.target != null && this.striking;
    }

    public boolean shouldSuppressGrimBoost() {
        if (!this.isToggled() || !this.maceAvailable || this.target == null
                || !(this.target.distanceTo(ClientAccess.evernightMinecraft.player) <= 10.0F)) {
            return false;
        }
        Entity movingTarget = this.target.isPassenger() ? this.target.getVehicle() : this.target;
        return MovementPhysics.getHorizontalSpeedKmh(movingTarget) <= 60.0D;
    }

    public boolean isFlightControlActive() {
        if (this.target != null) {
            return !this.recentFlightTimer.hasElapsed(100L) && this.maceAvailable;
        }
        return false;
    }

    private void equipElytra() {
        if (this.savedChestplateSlot == -1) {
            int elytraInventorySlot = InventoryUtils.findInventorySlot(stack -> {
                Item elytraItem = Items.ELYTRA;
                if (stack.is(elytraItem)) {
                    return DamageUtils.getDurabilityPercent(stack) > 1;
                }
                return false;
            }, false);
            int elytraContainerSlot = InventoryUtils.toMenuSlot(elytraInventorySlot);
            if (elytraContainerSlot != -1) {
                Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
                ArmorSlot chestSlotType = ArmorSlot.CHESTPLATE;
                int chestInventorySlot = chestSlotType.getInventoryIndex();
                ItemStack previousChestStack = inventory.getItem(chestInventorySlot);
                ArmorSlot chestContainerSlotType = ArmorSlot.CHESTPLATE;
                int chestContainerSlot = chestContainerSlotType.getContainerSlot();
                InventoryUtils.swapMenuSlots(elytraContainerSlot, chestContainerSlot);
                this.startGliding();
                if (this.isChestArmor(previousChestStack)) {
                    this.savedChestplateSlot = elytraContainerSlot;
                }
            }
        }
    }

    @EventHandler(priority = 100)
    private void onMaceSpoofPacket(PacketEvent.Send event) {
        if (!this.spoofSetting.getValue() || this.spoofSendingAttack
                || ClientAccess.evernightMinecraft.player == null
                || ClientAccess.evernightMinecraft.level == null) {
            return;
        }
        if (!(event.getPacket() instanceof ServerboundInteractPacket packet)
                || InteractPacketUtils.getAction(packet) != InteractPacketUtils.Action.ATTACK) {
            return;
        }
        Entity target = InteractPacketUtils.getEntity(packet);
        if (target == null) {
            return;
        }
        if (this.spoofIgnoreCrystalSetting.getValue() && target instanceof EndCrystal) {
            return;
        }
        if (!this.spoofTimer.hasElapsedAndReset(this.spoofDelaySetting.getValue())) {
            return;
        }
        CriticalsModule criticals = ClientContext.getModule(CriticalsModule.class);
        if (criticals != null && criticals.isToggled() && !this.spoofWithCriticalsSetting.getValue()) {
            return;
        }
        boolean onGround = ClientAccess.evernightMinecraft.player.onGround();
        if ((onGround && !this.spoofGroundSetting.getValue())
                || (!onGround && !this.spoofAirSetting.getValue())) {
            return;
        }
        if (this.spoofModeSetting.getValue() == MaceSpoofMode.HOLD) {
            if (!ItemUtils.isMace(ClientAccess.evernightMinecraft.player.getMainHandItem())) {
                return;
            }
            this.sendSpoofedPositions();
            return;
        }
        InventoryUtils.SwapSlot maceSlot = InventoryUtils.findSwapSlot(ItemUtils::isMace, false);
        if (!maceSlot.isFound() || maceSlot.hand() != InteractionHand.MAIN_HAND) {
            return;
        }
        event.cancel();
        int previousSlot = InventoryUtils.getSelectedSlot();
        InventoryUtils.swapTo(maceSlot);
        this.sendSpoofedPositions();
        this.spoofSendingAttack = true;
        PacketSender.sendAttack(target.getId());
        this.spoofSendingAttack = false;
        InventoryUtils.swapBack(previousSlot, maceSlot);
    }

    private void sendSpoofedPositions() {
        double x = ClientAccess.evernightMinecraft.player.getX();
        double y = ClientAccess.evernightMinecraft.player.getY();
        double z = ClientAccess.evernightMinecraft.player.getZ();
        boolean horizontalCollision = ClientAccess.evernightMinecraft.player.horizontalCollision;
        for (int index = 0; index < 4; index++) {
            PacketSender.sendDirect(new ServerboundMovePlayerPacket.Pos(x, y, z, false, horizontalCollision));
        }
        PacketSender.sendDirect(new ServerboundMovePlayerPacket.Pos(x, y + this.spoofHeightSetting.getValue(),
                z, false, horizontalCollision));
        PacketSender.sendDirect(new ServerboundMovePlayerPacket.Pos(x, y, z, false, horizontalCollision));
    }

    public enum MaceSpoofMode implements NamedEntry {
        HOLD("Hold"),
        SWAP("Swap");

        private final String name;

        MaceSpoofMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }
}