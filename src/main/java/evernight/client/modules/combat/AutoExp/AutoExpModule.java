package evernight.client.modules.combat.AutoExp;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.misc.AntiAim.AntiAimModule;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.render.text.Translations;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.ExperienceBottleRepairer;
import evernight.client.utils.inventory.ExperienceBottleUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

import java.util.function.Predicate;
import evernight.client.modules.combat.Offhand.OffhandModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class AutoExpModule extends Module {
    private static AntiAimModule antiAim;
    private static ElytraFlyModule elytraFly;

    static {
        antiAim = ClientContext.getModule(AntiAimModule.class);
        elytraFly = ClientContext.getModule(ElytraFlyModule.class);
    }

    public final Setting<ModeChoice> modeSetting;
    final Setting<Boolean> stopSetting;
    private final Setting<Integer> delaySetting;
    private final Setting<Integer> frequencySetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<Boolean> airSetting;
    private final Setting<Boolean> inventorySetting;
    private final Setting<Integer> stopAtSetting;
    private final Setting<Boolean> autoDisableSetting;
    public final Setting<Boolean> toolsSetting;
    private final ElapsedTimer throwTimer;
    public boolean stopped;
    private int swappedHotbarSlot = -1;
    private int swappedInventorySlot = -1;
    private int originalSelectedSlot;
    private boolean active;

    public AutoExpModule() {
        super("AutoExp", ModuleCategory.COMBAT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.SILENT));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 2, 0, 5));
        this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 16));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
        this.airSetting = this.add(new BooleanSetting("Air", true));
        this.inventorySetting = this.add(new BooleanSetting("Inventory", true));
        this.stopSetting = this.add(new BooleanSetting("Stop", true).markVisibilityDependency());
        Predicate<Object> stopAtVisibility = unused -> {
            return this.stopSetting.isExpanded();
        };
        this.stopAtSetting = this.add(new NumberSetting<>("StopAt", 100, 80, 100, stopAtVisibility).withUnit("%"));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).dependsOn(this.stopSetting));
        this.toolsSetting = this.add(new BooleanSetting("Tools", false).dependsOn(this.stopSetting));
        this.throwTimer = new ElapsedTimer();
        this.stopped = false;
    }

    private static void performMending(AutoExpModule owner) {
        if (!owner.shouldPauseMending()) {
            float[] targetRotation = owner.calculateThrowRotations();
            InventoryUtils.SwapSlot bottleSlot = ExperienceBottleRepairer.findBottleSlot();
            LocalPlayer holdingPlayer = ClientAccess.evernightMinecraft.player;
            Item bottleItem = Items.EXPERIENCE_BOTTLE;
            boolean holdingBottles = holdingPlayer.isHolding(bottleItem);
            if (!holdingBottles) {
                if (!bottleSlot.isFound()) {
                    if (owner.swapBottlesFromInventory()) {
                        holdingBottles = true;
                    }
                }
            }
            mendingStopped:
            {
                if (owner.stopSetting.getValue()) {
                    boolean allItemsRepaired = !ExperienceBottleRepairer.anyGearNeedsRepair(
                            owner.toolsSetting.getValue(), owner.stopAtSetting.getValue());
                    if (allItemsRepaired) {
                        break mendingStopped;
                    }
                    if (!bottleSlot.isFound() && !holdingBottles) {
                        break mendingStopped;
                    }
                }
                if (bottleSlot.isFound() || holdingBottles) {
                    ElapsedTimer delayTimer = owner.throwTimer;
                    int delayTicks = owner.delaySetting.getValue();
                    long delayMillis = delayTicks * 50L;
                    if (delayTimer.hasElapsed(delayMillis)) {
                        owner.stopped = false;
                        InteractionHand throwHand = bottleSlot.isFound()
                                ? bottleSlot.hand()
                                : InteractionHand.MAIN_HAND;
                        boolean silent = owner.modeSetting.getValue() == AutoExpModule.ModeChoice.SILENT;
                        ExperienceBottleUtils.throwBottles(throwHand, bottleSlot,
                                owner.frequencySetting.getValue(),
                                owner.rotateSetting.getValue() ? targetRotation : null,
                                silent);
                        owner.throwTimer.reset();
                    }
                }
                if (owner.rotateSetting.getValue()) {
                    ClientContext.rotationManager.requestRotation(targetRotation, 400);
                }
                owner.active = true;
                return;
            }
            owner.stopped = true;
            if (owner.autoDisableSetting.getValue()) {
                owner.disable();
            }
        }
    }

    private static void restoreInventorySwap(AutoExpModule owner) {
        if (owner.swappedInventorySlot != -1) {
            int inventorySlot = owner.swappedInventorySlot;
            int hotbarSlot = owner.swappedHotbarSlot;
            ClickType clickType = ClickType.SWAP;
            InventoryUtils.clickSlot(inventorySlot, hotbarSlot, clickType);
            owner.swappedInventorySlot = -1;
            owner.swappedHotbarSlot = -1;
        }
    }

    public static void bindReferencedModules(ModuleRegistry modules) {
        antiAim = modules.getModule(AntiAimModule.class);
        elytraFly = modules.getModule(ElytraFlyModule.class);
    }

    public boolean isActive() {
        return this.active;
    }

    @Override
    public String getInfo() {
        if (this.stopped) {
            return Translations.ui("modules.AutoExp.StoppedInfo");
        } else {
            return String.valueOf(ExperienceBottleRepairer.countBottles());
        }
    }

    @Override
    public void onEnable() {
        if (!this.isWorldUnavailable()) {
            if (this.rotateSetting.getValue()) {
                this.throwTimer.reset();
            }
            if (antiAim.isToggled() && !this.stopped) {
                antiAim.pauseRotations();
            }
            Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
            this.originalSelectedSlot = inventory.getSelectedSlot();
        }
    }

    @Override
    public void onDisable() {
        this.active = false;
        if (this.isWorldUnavailable()) {
            this.swappedInventorySlot = -1;
        } else {
            restoreInventorySwap(this);
            if (ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot() != this.originalSelectedSlot) {
                if (this.modeSetting.getValue() == AutoExpModule.ModeChoice.NORMAL) {
                    InventoryUtils.selectHotbarSlotForPlacement(this.originalSelectedSlot);
                }
            }
        }
    }

    @EventHandler(priority = 250)
    private void updateMending(RotationRequestEvent event) {
        this.active = false;
        performMending(this);
    }

    private boolean swapBottlesFromInventory() {
        if (!this.inventorySetting.getValue()) {
            return false;
        }
        Item bottleItem = Items.EXPERIENCE_BOTTLE;
        int bottleInventorySlot = InventoryUtils.findMenuSlot(bottleItem);
        int selectedHotbarSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        if (bottleInventorySlot == -1) {
            return false;
        }
        int targetHotbarSlot = selectedHotbarSlot;
        if (this.toolsSetting.getValue()) {
            // Keep the held tool in the hand: park the bottle in another free hotbar slot so the
            // ghost hand swap can use it without displacing the tool that is being repaired.
            int emptyHotbarSlot = this.findEmptyHotbarSlot(selectedHotbarSlot);
            if (emptyHotbarSlot != -1) {
                targetHotbarSlot = emptyHotbarSlot;
            }
        }
        ClickType clickType = ClickType.SWAP;
        InventoryUtils.clickSlot(bottleInventorySlot, targetHotbarSlot, clickType);
        this.swappedInventorySlot = bottleInventorySlot;
        this.swappedHotbarSlot = targetHotbarSlot;
        return true;
    }

    private int findEmptyHotbarSlot(int exceptSlot) {
        Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (slot != exceptSlot && inventory.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean shouldPauseMending() {
        if (!this.airSetting.getValue()) {
            HitResult hitResult = ClientAccess.evernightMinecraft.hitResult;
            if (hitResult.getType() == Type.MISS) {
                return true;
            }
            if (ClientAccess.evernightMinecraft.hitResult instanceof BlockHitResult blockHit) {
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                BlockPos position = blockHit.getBlockPos();
                BlockState state = level.getBlockState(position);
                if (state.canBeReplaced()) {
                    return true;
                }
            }
        }
        if (!OffhandModule.isRecentSwapWindowActive()) {
            return ClientAccess.evernightMinecraft.player.isFallFlying() && !this.canMendDuringFlight();
        } else {
            return true;
        }
    }

    private boolean canMendDuringFlight() {
        if (!this.rotateSetting.getValue()) {
            return false;
        }
        ElytraFlyModule.ModeChoice mode = elytraFly.modeSetting.getValue();
        return elytraFly.isToggled()
                && PlayerStateUtils.isFallFlying() && !MovementPhysics.isLocalPlayerMoving()
                && (mode == ElytraFlyModule.ModeChoice.CONTROL || mode == ElytraFlyModule.ModeChoice.NEWGRIM);
    }

    private float[] calculateThrowRotations() {
        if (this.canMendDuringFlight()) {
            return new float[]{ClientAccess.evernightMinecraft.player.getYRot(), -90.0F};
        }
        AABB playerBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.evernightMinecraft.player, 1);
        return RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(playerBounds));
    }

    public enum ModeChoice implements NamedEntry {
        NORMAL("Normal"),
        SILENT("Silent");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}