package evernight.client.modules.combat.Aura;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.AuraAttackEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.settings.options.*;
import evernight.client.utils.core.LockUtils;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.combat.TargetSelector;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.EnchantmentUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.function.Predicate;
import evernight.client.modules.combat.AutoMace.AutoMaceModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public final class AuraModule extends Module {
    private static final InventoryUtils.SwapSlot NO_SWAP = new InventoryUtils.SwapSlot(-1, false, InteractionHand.MAIN_HAND);
    private static AntiCheatModule antiCheat;
    private static MultiTaskModule multiTask;
    private static AutoMaceModule autoMace;

    static {
        antiCheat = ClientContext.getModule(AntiCheatModule.class);
        multiTask = ClientContext.getModule(MultiTaskModule.class);
        autoMace = ClientContext.getModule(AutoMaceModule.class);
    }

    public final Setting<Float> rangeSetting;
    public final Setting<Float> wallRangeSetting;
    final Setting<Float> delaySetting;
    final Setting<Boolean> extraRangeSetting;
    final Setting<Boolean> tPSSyncSetting;
    final Setting<Boolean> renderSetting;
    final Setting<AuraTypeChoice> typeSetting;
    final Setting<Color> fillSetting;
    final Setting<Color> outlineSetting;
    final Setting<Float> lineWidthSetting;
    final Setting<AuraWeaponChoice> weaponSetting;
    final Setting<Boolean> targetsSetting;
    private final Setting<EntitySortChoice> sortingSetting;
    private final Setting<Float> fovSetting;
    private final Setting<Boolean> pauseBaritoneSetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<Boolean> magnetSetting;
    private final Setting<Integer> offsetSetting;
    private final Setting<Float> limitSetting;
    private final Setting<Boolean> weaponsSetting;
    private final Setting<Boolean> shieldBreakerSetting;
    private final Setting<AuraSwapChoice> swapSetting;
    private final Setting<AuraMaceChoice> maceSetting;
    private final Setting<Boolean> alternativeMaceSetting;
    private final Setting<Boolean> playersSetting;
    private final Setting<Boolean> invisibleSetting;
    private final Setting<Boolean> ignoreNakedsSetting;
    private final Setting<Boolean> animalsSetting;
    private final Setting<Boolean> villagersSetting;
    private final Setting<Boolean> hostilesSetting;
    private final Setting<Boolean> neutralsSetting;
    private final Setting<Boolean> crystalsSetting;
    private final Setting<Boolean> projectilesSetting;
    private final Setting<Boolean> vehiclesSetting;
    private final Setting<Boolean> ignoreNamedSetting;
    private final AuraAttackCooldown attackCooldown;
    private final AuraAttackReadiness attackReadiness;
    private final AuraTargetRenderer targetRenderer;
    public boolean criticalAttackFlag;
    private int restoreSlot;
    private Entity target;
    private Entity crosshairTarget;

    public AuraModule() {
        super("Aura", ModuleCategory.COMBAT, "killaura", "forcefield", "ka");
        this.sortingSetting = this.add(new EnumSetting<>("Sorting", EntitySortChoice.DISTANCE));
        this.fovSetting = this.add(new NumberSetting<>("Fov", 180.0F, 0.0F, 180.0F).withUnit("°"));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 1.0F, 0.0F, 1.0F));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 3.0F, 1.0F, 6.5F).withUnit("m"));
        this.wallRangeSetting = this.add(new NumberSetting<>("WallRange", 3.6F, 0.0F, 6.5F).withUnit("m"));
        this.extraRangeSetting = this.add(new BooleanSetting("ExtraRange", true));
        this.tPSSyncSetting = this.add(new BooleanSetting("TPSSync", true));
        Predicate<Object> pauseBaritoneVisibility = unused -> ClientContext.baritoneBridge.isAvailable();
        this.pauseBaritoneSetting = this.add(new BooleanSetting("PauseBaritone", true, pauseBaritoneVisibility));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true).markVisibilityDependency());
        this.magnetSetting = this.add(new BooleanSetting("Magnet", false).dependsOn(this.rotateSetting));
        this.offsetSetting = this.add(new NumberSetting<>("Offset", 180, -90, 180).withUnit("°").dependsOn(this.magnetSetting, this.rotateSetting));
        this.limitSetting = this.add(new NumberSetting<>("Limit", 1.0F, 0.0F, 1.0F).dependsOn(this.rotateSetting));
        this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
        this.typeSetting = this.add(new EnumSetting<>("Type", AuraTypeChoice.BOX).dependsOn(this.renderSetting));
        Predicate<Object> fillVisibility = unused -> this.renderSetting.isExpanded();
        this.fillSetting = this.add(new ColorSetting("Fill", new Color(1270716927, true), fillVisibility));
        Predicate<Object> outlineVisibility = unused -> this.renderSetting.isExpanded();
        this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-759326209, true), outlineVisibility));
        Predicate<Object> lineWidthVisibility = unused -> this.renderSetting.isExpanded();
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 2.0F, lineWidthVisibility));
        this.weaponsSetting = this.add(new BooleanSetting("Weapons", false).markAsGroup().markVisibilityDependency());
        this.shieldBreakerSetting = this.add(new BooleanSetting("ShieldBreaker", true).dependsOn(this.weaponsSetting));
        this.weaponSetting = this.add(new EnumSetting<>("Weapon", AuraWeaponChoice.REQUIRE).dependsOn(this.weaponsSetting));
        Predicate<Object> swapVisibility = unused -> this.weaponSetting.getValue() == AuraWeaponChoice.SWAP;
        this.swapSetting = this.add(new EnumSetting<>("Swap", AuraSwapChoice.NORMAL, swapVisibility).dependsOn(this.weaponsSetting));
        this.maceSetting = this.add(new EnumSetting<>("Mace", AuraMaceChoice.NONE).dependsOn(this.weaponsSetting));
        this.alternativeMaceSetting = this.add(new BooleanSetting("AlternativeMace", false).dependsOn(this.weaponsSetting));
        this.targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency());
        this.playersSetting = this.add(new BooleanSetting("Players", true).markVisibilityDependency().dependsOn(this.targetsSetting));
        this.invisibleSetting = this.add(new BooleanSetting("Invisible", true).dependsOn(this.targetsSetting, this.playersSetting));
        this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.targetsSetting, this.playersSetting));
        Predicate<Object> animalsVisibility = unused -> this.targetsSetting.isExpanded();
        this.animalsSetting = this.add(new BooleanSetting("Animals", false, animalsVisibility));
        this.villagersSetting = this.add(new BooleanSetting("Villagers", false).dependsOn(this.targetsSetting));
        this.hostilesSetting = this.add(new BooleanSetting("Hostiles", true).dependsOn(this.targetsSetting));
        this.neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
        this.crystalsSetting = this.add(new BooleanSetting("Crystals", false).dependsOn(this.targetsSetting));
        this.projectilesSetting = this.add(new BooleanSetting("Projectiles", false).dependsOn(this.targetsSetting));
        this.vehiclesSetting = this.add(new BooleanSetting("Vehicles", false).dependsOn(this.targetsSetting));
        this.ignoreNamedSetting = this.add(new BooleanSetting("IgnoreNamed", false).dependsOn(this.targetsSetting));
        this.attackCooldown = new AuraAttackCooldown(this);
        this.attackReadiness = new AuraAttackReadiness(this);
        this.targetRenderer = new AuraTargetRenderer(this);
        this.restoreSlot = -1;
        this.rangeSetting.setBoundaryLabel("Vanilla", SettingLabelCondition.MAX);
        this.wallRangeSetting.setBoundaryLabel("Vanilla", SettingLabelCondition.MAX);
        this.fovSetting.setBoundaryLabel("OnSight", SettingLabelCondition.MIN);
        this.limitSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
        this.pauseBaritoneSetting.hideWithoutBaritone();
    }

    public static void bindModuleRegistry(ModuleRegistry modules) {
        antiCheat = modules.getModule(AntiCheatModule.class);
        multiTask = modules.getModule(MultiTaskModule.class);
        autoMace = modules.getModule(AutoMaceModule.class);
        AuraAttackReadiness.bindModuleRegistry(modules);
    }

    private boolean isWithinFov(Entity candidate) {
        if (this.fovSetting.isAtMinimum()) {
            return candidate == this.crosshairTarget;
        }
        float[] angles = RotationManager.getRotationsToEntity(candidate);
        float targetYaw = angles[0];
        float playerYaw = ClientAccess.evernightMinecraft.player.getYRot();
        float yawDifference = Mth.degreesDifferenceAbs(targetYaw, playerYaw);
        float pitchDelta = ClientAccess.evernightMinecraft.player.getXRot() - angles[1];
        float pitchDifference = Math.abs(pitchDelta);
        Float yawLimit = this.fovSetting.getValue();
        return yawDifference <= yawLimit && pitchDifference <= this.fovSetting.getValue();
    }

    private boolean isSwapWeaponMode() {
        return this.weaponSetting.getValue() == AuraWeaponChoice.SWAP;
    }

    /**
     * Switches the weapon mode to {@link AuraWeaponChoice#SWAP}, used by modules that link the aura and
     * need it to fetch the best weapon on its own instead of requiring one in hand.
     */
    public void applySwapWeaponMode() {
        this.weaponSetting.setValue(AuraWeaponChoice.SWAP);
    }

    /**
     * @return the currently selected weapon mode, so modules that temporarily switch it can restore it
     */
    public AuraWeaponChoice getWeaponMode() {
        return this.weaponSetting.getValue();
    }

    /**
     * Restores a weapon mode captured before a module temporarily switched it.
     */
    public void setWeaponMode(AuraWeaponChoice weaponMode) {
        this.weaponSetting.setValue(weaponMode);
    }

    float getAttackDelay() {
        if (this.target == null) {
            return this.delaySetting.getValue();
        }
        Entity capturedMaceTarget = this.target;
        InventoryUtils.SwapSlot maceSlot = this.findMaceSlot(capturedMaceTarget);
        if (maceSlot.isFound()) {
            float fallDistance = PlayerStateUtils.getTrackedFallDistance();
            if (fallDistance > 1.0F) {
                Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
                return maceSlot.slot() == inventory.getSelectedSlot() ? 0.1F : 0.25F;
            }
        }
        return this.delaySetting.getValue();
    }

    private void restoreWeaponSlot() {
        if (this.isSwapWeaponMode() && this.swapSetting.getValue() == AuraSwapChoice.SWAPBACK && this.restoreSlot != -1) {
            InventoryUtils.selectHotbarSlotForPlacement(this.restoreSlot);
            this.restoreSlot = -1;
        }
    }

    public boolean isTargetWithinAttackRange() {
        if (this.target == null) {
            return false;
        }
        Entity distanceTarget = this.target;
        boolean useExtraRange = this.extraRangeSetting.getValue();
        double distance = WorldUtils.getEntityDistance(distanceTarget, useExtraRange);
        boolean visible = RaycastUtils.hasVisibleEntitySample(this.target);
        float attackRange = this.getEffectiveRange(visible ? this.rangeSetting : this.wallRangeSetting);
        return distance <= attackRange;
    }

    public Entity getTarget() {
        return this.target;
    }

    private void updateCrosshairTarget() {
        if (!this.fovSetting.isAtMinimum()) {
            this.crosshairTarget = null;
        } else if (ClientAccess.evernightMinecraft.hitResult instanceof EntityHitResult hit) {
            this.crosshairTarget = hit.getEntity();
        } else {
            this.crosshairTarget = null;
        }
    }

    /**
     * @return whether the player is holding a weapon. With automatic weapon switching off the shield
     * breaker may only borrow an axe in that case, so it never pulls the hotbar away from a tool,
     * food or a block the player is about to use
     */
    private boolean isHoldingWeapon() {
        ItemStack heldStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        return ItemUtils.isAxe(heldStack) || ItemUtils.isSword(heldStack) || ItemUtils.isSpear(heldStack)
                || ItemUtils.isMace(heldStack) || heldStack.getItem() instanceof TridentItem;
    }

    /**
     * @return the hotbar axe used for one hit on a shielding target, or {@link #NO_SWAP} when the shield
     * breaker is off, the target is not blocking, the player is not fighting with a weapon while
     * automatic weapon switching is off, or no axe is available. Independent of the weapon mode
     * on purpose: the shield breaker works even when automatic weapon switching is off.
     */
    private InventoryUtils.SwapSlot findShieldBreakerSlot() {
        if (!this.shieldBreakerSetting.getValue()
                || !(this.target instanceof LivingEntity livingTarget) || !livingTarget.isBlocking()) {
            return NO_SWAP;
        }
        if (!this.isSwapWeaponMode() && !this.isHoldingWeapon()) {
            return NO_SWAP;
        }
        InventoryUtils.SwapSlot axeSlot = InventoryUtils.findSwapSlot(ItemUtils::isAxe, false);
        return axeSlot.isFound() && axeSlot.hand() == InteractionHand.MAIN_HAND ? axeSlot : NO_SWAP;
    }

    private InventoryUtils.SwapSlot findWeaponSlot() {
        InventoryUtils.SwapSlot shieldBreakerSlot = this.findShieldBreakerSlot();
        if (shieldBreakerSlot.isFound()) {
            return shieldBreakerSlot;
        }
        if (!this.isSwapWeaponMode()) {
            return new InventoryUtils.SwapSlot(InventoryUtils.getSelectedSlot(), true, InteractionHand.MAIN_HAND);
        }
        Predicate<ItemStack> weaponPredicate = stack ->
                ItemUtils.isAxe(stack) || ItemUtils.isSword(stack) || ItemUtils.isSpear(stack);
        return InventoryUtils.findSwapSlot(weaponPredicate, false);
    }

    @Override
    public String getInfo() {
        if (this.target != null && this.target instanceof Player) {
            Component targetName = this.target.getName();
            return targetName.getString();
        } else {
            return null;
        }
    }

    @Override
    public void onToggle() {
        this.target = null;
        ClientContext.baritoneBridge.releasePause(this);
    }

    @Override
    public void onEnable() {
        ClientAccess.eventBus.subscribe(this.targetRenderer);
    }

    @Override
    public void onDisable() {
        ClientAccess.eventBus.unsubscribe(this.targetRenderer);
        if (this.isWorldUnavailable()) {
            this.restoreSlot = -1;
        } else {
            this.restoreWeaponSlot();
        }
    }

    private void attackCurrentTarget() {
        if (this.target == null) {
            return;
        }
        Entity capturedVisibilityTarget = this.target;
        float attackRange;
        if (RaycastUtils.hasVisibleEntitySample(capturedVisibilityTarget)) {
            Setting<Float> capturedRange = this.rangeSetting;
            attackRange = this.getEffectiveRange(capturedRange);
        } else {
            Setting<Float> capturedWallRange = this.wallRangeSetting;
            attackRange = this.getEffectiveRange(capturedWallRange);
        }
        Entity capturedDistanceTarget = this.target;
        boolean useExtraRange = this.extraRangeSetting.getValue();
        if (WorldUtils.getEntityDistance(capturedDistanceTarget, useExtraRange) > attackRange) {
            return;
        }
        int originalSlot = InventoryUtils.getSelectedSlot();
        InventoryUtils.SwapSlot shieldBreakerSlot = this.findShieldBreakerSlot();
        InventoryUtils.SwapSlot weaponSlot = shieldBreakerSlot.isFound() ? shieldBreakerSlot : this.findWeaponSlot();
        // With automatic weapon switching off the axe is only borrowed for this hit, so the attack is
        // followed by a silent swap back to the slot the player was holding.
        boolean shieldBreakSwap = shieldBreakerSlot.isFound() && !this.isSwapWeaponMode();
        boolean swapRequired = this.isSwapWeaponMode() || shieldBreakSwap;
        if (this.target instanceof EndCrystal) {
            LocalPlayer weaknessPlayer = ClientAccess.evernightMinecraft.player;
            Holder weaknessEffect = MobEffects.WEAKNESS;
            swapRequired = weaknessPlayer.hasEffect(weaknessEffect);
            if (swapRequired) {
                LocalPlayer strengthPlayer = ClientAccess.evernightMinecraft.player;
                Holder strengthEffect = MobEffects.STRENGTH;
                swapRequired = !strengthPlayer.hasEffect(strengthEffect);
            }
        }
        boolean silentSwap = shieldBreakSwap
                || this.swapSetting.getValue() == AuraSwapChoice.SILENT && swapRequired;
        Entity capturedMaceTarget = this.target;
        InventoryUtils.SwapSlot maceSlot = this.findMaceSlot(capturedMaceTarget);
        if (maceSlot.isFound()) {
            weaponSlot = maceSlot;
            swapRequired = true;
            silentSwap = true;
        }
        AuraAttackReadiness capturedReadiness = this.attackReadiness;
        Entity capturedReadinessTarget = this.target;
        float attackStrength = this.attackCooldown.getAttackStrength(weaponSlot.isFound()
                ? weaponSlot.getStack() : ClientAccess.evernightMinecraft.player.getMainHandItem());
        if (!capturedReadiness.canAttack(capturedReadinessTarget, attackStrength)) {
            return;
        }
        boolean inventorySwap = weaponSlot.isFound() && !weaponSlot.hotbar() && !weaponSlot.isInHand();
        if (swapRequired && inventorySwap && ClientAccess.evernightMinecraft.player.containerMenu != ClientAccess.evernightMinecraft.player.inventoryMenu) {
            return;
        }
        if (swapRequired) {
            if (multiTask.shouldPauseModule(this)) {
                if (ClientAccess.evernightMinecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND && weaponSlot.slot() != originalSlot && weaponSlot.isFound()) {
                    return;
                }
            }
            InventoryUtils.swapTo(weaponSlot);
            if (originalSlot != weaponSlot.slot() && !silentSwap) {
                this.restoreSlot = originalSlot;
            }
        }
        try {
            EventBus capturedPreBus = ClientAccess.eventBus;
            AuraAttackEvent preEvent = new AuraAttackEvent(EventStage.Pre, this.target.getId());
            capturedPreBus.post(preEvent);
            Entity capturedAttackTarget = this.target;
            this.sendAttack(capturedAttackTarget);
            boolean useMagnet = this.magnetSetting.getValue() && this.target.getType() != EntityType.CREEPER
                    && this.target.getType() != EntityType.END_CRYSTAL;
            if (this.rotateSetting.getValue() && useMagnet) {
                float[] rotations = RotationManager.getRotationsToEntity(this.target);
                float targetYaw = rotations[0];
                Integer offsetValue = this.offsetSetting.getValue();
                float offsetYaw = targetYaw + offsetValue.intValue();
                rotations[0] = Mth.wrapDegrees(offsetYaw);
                ClientContext.rotationManager.requestRotation(rotations, 4);
            }
        } finally {
            if (silentSwap && weaponSlot.isFound()) {
                InventoryUtils.swapBack(originalSlot, weaponSlot);
            }
        }
        EventBus capturedPostBus = ClientAccess.eventBus;
        AuraAttackEvent postEvent = new AuraAttackEvent(EventStage.Post, this.target.getId());
        capturedPostBus.post(postEvent);
    }

    public float getEffectiveRange(Setting<Float> rangeSetting) {
        if (rangeSetting.isAtMaximum()) {
            InventoryUtils.SwapSlot weaponSlot = this.findWeaponSlot();
            ItemStack capturedWeaponStack;
            if (!weaponSlot.isFound()) {
                capturedWeaponStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
            } else {
                capturedWeaponStack = weaponSlot.getStack();
            }
            ItemStack weaponStack = capturedWeaponStack;
            DataComponentType<AttackRange> attackRangeComponent = DataComponents.ATTACK_RANGE;
            AttackRange attackRange = weaponStack.get(attackRangeComponent);
            if (attackRange == null) {
                return 3.0F;
            } else {
                return attackRange.maxRange();
            }
        } else {
            return rangeSetting.getValue();
        }
    }

    private boolean shouldAttackDuringRotationCollection() {
        return autoMace.isToggled() && autoMace.isStrikingTarget() && RotationManager.isMovementSyncEnabled()
                && antiCheat.rotationsSetting.getValue() == AntiCheatRotationsChoice.MOTION;
    }

    @EventHandler
    private void onPlayerUpdate(RotationRequestEvent event) {
        this.updateCrosshairTarget();
        boolean targetDead = this.target != null && !this.target.isAlive();
        boolean multiTaskBlocked = multiTask.shouldPauseModule(this) && multiTask.getMode() == MultiTaskModeChoice.NONE;
        Item heldItem = ClientAccess.evernightMinecraft.player.getMainHandItem().getItem();
        boolean missingWeapon = !this.isAllowedWeapon(heldItem) && this.weaponSetting.getValue() == AuraWeaponChoice.REQUIRE;
        if (autoMace.isToggled() && autoMace.isStrikingTarget()) {
            missingWeapon = false;
        }
        if (missingWeapon || targetDead || multiTaskBlocked) {
            this.target = null;
            return;
        }
        if (ClientAccess.evernightMinecraft.player.containerMenu != ClientAccess.evernightMinecraft.player.inventoryMenu
                && antiCheat.is2b2tEnabled()) {
            return;
        }
        float searchPadding = 0.0F;
        if (this.limitSetting.isAtMaximum() && RotationManager.isMovementSyncEnabled()
                && (!PlayerStateUtils.isFallFlying() || RotationManager.isSilentRotationMode())) {
            searchPadding = 0.1F;
        }
        TargetSelector targetFilter = this.buildTargetFilter(searchPadding);
        this.target = targetFilter.select();
        ClientContext.baritoneBridge.releasePause(this);
        if (this.pauseBaritoneSetting.getValue() && this.target != null) {
            ClientContext.baritoneBridge.requestPause(this);
        }
        if (this.target == null) {
            this.restoreWeaponSlot();
            return;
        }
        float attackStrength = this.attackCooldown.getSlotAttackStrength(this.findWeaponSlot());
        if (RotationManager.isMovementSyncEnabled()) {
            Entity readinessTarget = this.target;
            if (!this.attackReadiness.canAttack(readinessTarget, attackStrength)) {
                return;
            }
        }
        if (this.shouldAttackDuringRotationCollection()) {
            ClientContext.rotationManager.executeAction(this::attackCurrentTarget);
        } else {
            LockUtils.runWithSharedLock(this::attackCurrentTarget);
        }
        if (!this.rotateSetting.getValue()) {
            return;
        }
        if (!RotationManager.isMovementSyncEnabled()) {
            if (this.limitSetting.getValue() - 0.0F != 0.0F) {
                float limit = this.limitSetting.getValue();
                if (limit * this.getAttackDelay() > attackStrength) {
                    return;
                }
            }
        }
        Entity capturedTarget = this.target;
        boolean capturedExtraRangeFlag = this.extraRangeSetting.getValue();
        WorldUtils.EntityRange rangeResult = WorldUtils.getEntityRange(capturedTarget, capturedExtraRangeFlag);
        Vec3 rawAimOrigin = rangeResult.origin();
        Vec3 aimOrigin = this.adjustAimOrigin(rawAimOrigin);
        AABB targetBounds = this.target.getBoundingBox();
        Vec3 aimPoint = BoundingBoxUtils.clampToBounds(aimOrigin, targetBounds);
        float[] rotations = RotationManager.calculateRotations(aimOrigin, aimPoint);
        ClientContext.rotationManager.requestRotation(rotations, 5);
    }

    private TargetSelector buildTargetFilter(float searchPadding) {
        TargetSelector filter = new TargetSelector().withPlayers(this.playersSetting.getValue())
                .withNeutrals(this.neutralsSetting.getValue()).withAnimals(this.animalsSetting.getValue())
                .withVillagers(this.villagersSetting.getValue()).withHostiles(this.hostilesSetting.getValue())
                .withCrystals(this.crystalsSetting.getValue()).withProjectiles(this.projectilesSetting.getValue())
                .withVehicles(this.vehiclesSetting.getValue());
        float wallSearchRange = this.getEffectiveRange(this.wallRangeSetting) + searchPadding;
        filter.wallRange(wallSearchRange);
        float visibleSearchRange = this.getEffectiveRange(this.rangeSetting) + searchPadding;
        filter.visibleRange(visibleSearchRange).ignoreNamed(this.ignoreNamedSetting.getValue())
                .ignoreNakeds(this.ignoreNakedsSetting.getValue())
                .ignoreInvisibles(!this.invisibleSetting.getValue());
        filter.sortBy((this.sortingSetting.getValue()).getComparator()).filter(this::isWithinFov);
        return filter.useExtraRange(this.extraRangeSetting.getValue());
    }

    private InventoryUtils.SwapSlot findMaceSlot(Entity candidate) {
        if (candidate instanceof Player) {
            if (!PlayerStateUtils.hasArmor((LivingEntity) candidate)) {
                if (!autoMace.nakedsSetting.getValue()) {
                    return NO_SWAP;
                }
            }
        }
        if (autoMace.isToggled()) {
            if (autoMace.isStrikingTarget()) {
                Predicate strikingMacePredicate = (java.util.function.Predicate<net.minecraft.world.item.ItemStack>) ItemUtils::isMace;
                return InventoryUtils.findSwapSlot(strikingMacePredicate, this.alternativeMaceSetting.getValue());
            }
        }
        boolean densityAllowed = !ClientAccess.evernightMinecraft.player.isFallFlying() && PlayerStateUtils.getTrackedFallDistance() > 2.0F;
        Predicate<ItemStack> configuredMacePredicate = candidateStack ->
                ItemUtils.isMace(candidateStack) && (this.maceSetting.getValue()).matches(candidateStack);
        InventoryUtils.SwapSlot maceSlot = InventoryUtils.findSwapSlot(configuredMacePredicate, this.alternativeMaceSetting.getValue());
        if (maceSlot.isFound()) {
            ItemStack maceStack = maceSlot.getStack();
            ResourceKey densityEnchantment = Enchantments.DENSITY;
            if (EnchantmentUtils.hasEnchantment(densityEnchantment, maceStack) && !densityAllowed) {
                return NO_SWAP;
            }
        }
        if (this.maceSetting.getValue() == AuraMaceChoice.SMART) {
            float smartFallDistance = PlayerStateUtils.getTrackedFallDistance();
            if (smartFallDistance > 3.0F && !maceSlot.isFound()) {
                Predicate fallbackMacePredicate = (java.util.function.Predicate<net.minecraft.world.item.ItemStack>) ItemUtils::isMace;
                return InventoryUtils.findSwapSlot(fallbackMacePredicate, this.alternativeMaceSetting.getValue());
            }
        }
        return maceSlot;
    }

    private Vec3 adjustAimOrigin(Vec3 origin) {
        if (RotationManager.isMovementSyncEnabled() && !RotationManager.isSilentRotationMode()) {
            Vec3 motionOffset = ClientContext.movementPredictionManager.getAdjustedLocalMotion();
            return origin.add(motionOffset);
        }
        return origin;
    }

    private boolean isAllowedWeapon(Item item) {
        ItemStack stack = item.getDefaultInstance();
        if (ItemUtils.isSword(stack) || item instanceof TridentItem || item instanceof AxeItem
                || ItemUtils.isMace(stack) || ItemUtils.isSpear(stack)) {
            return true;
        }
        if (!stack.is(ItemTags.PICKAXES)) {
            return false;
        }
        Predicate<ItemStack> alternativeWeaponPredicate = candidateStack ->
                ItemUtils.isSword(candidateStack) || candidateStack.getItem() instanceof AxeItem;
        return InventoryUtils.findHotbarSlot(alternativeWeaponPredicate) == -1;
    }

    private void sendAttack(Entity target) {
        boolean wasSprinting = ClientAccess.evernightMinecraft.player.isSprinting();
        boolean desiredSprint = !(this.magnetSetting.getValue() && this.rotateSetting.getValue());
        boolean changeSprint = desiredSprint == wasSprinting;
        if (RotationManager.is2b2tMode() && RotationManager.isMovementSyncEnabled() && wasSprinting && changeSprint) {
            changeSprint = false;
        }
        if (target.getType() == EntityType.END_CRYSTAL) {
            changeSprint = false;
        }
        if (changeSprint) {
            Action action = wasSprinting ? Action.STOP_SPRINTING : Action.START_SPRINTING;
            PacketSender.sendPlayerCommand(action);
        }
        BlockInteractionUtils.attackEntity(target);
        if (changeSprint) {
            Action action = wasSprinting ? Action.START_SPRINTING : Action.STOP_SPRINTING;
            PacketSender.sendPlayerCommand(action);
        }
        if (!PlayerStateUtils.isFallFlying()) {
            ClientAccess.evernightMinecraft.player.attack(target);
        }
        ClientAccess.evernightMinecraft.player.resetAttackStrengthTicker();
    }
}