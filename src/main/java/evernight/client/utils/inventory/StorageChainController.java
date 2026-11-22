package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.world.BaritoneBlockTask;
import evernight.client.utils.world.StoragePlacement;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reusable storage chain for the automatic mining modules.
 *
 * <p>The controller owns the whole asynchronous flow around a placed container:
 * <ol>
 *   <li>find a storage spot (a carved pocket, or an existing surface 3x3x3 space),</li>
 *   <li>place a shulker box and move loot into it,</li>
 *   <li>when no empty box is left, place an ender chest and swap full boxes for empty ones,</li>
 *   <li>recover every placed block and pick the drops back up.</li>
 * </ol>
 *
 * <p>It also supports the three item-fetching variants used by the repair controller: place a box
 * from the inventory and take items out, take a box out of the ender chest, and put a box back into
 * the ender chest.
 *
 * <p>All world interaction is delegated to existing utilities ({@link BaritoneBlockTask},
 * {@link StoragePlacement}, {@link ShulkerBoxUtils}, {@link ContainerInteractionUtils}); this class
 * only owns the state machine.
 */
public final class StorageChainController implements ClientAccess {
    /**
     * Result of one controller tick.
     */
    public enum Status {
        IDLE,
        RUNNING,
        /**
         * the host has to finish its own preparation (AutoMining's crafting table) and call
         * {@link #resumeStorage()}.
         */
        PREPARING,
        /**
         * Waiting for a silk touch pickaxe before the ender chest is placed; without one the chest
         * would be consumed when the chain breaks it again.
         */
        PAUSED,
        DONE,
        FAILED
    }

    /**
     * How the storage spot is obtained.
     */
    public enum Setup {
        /**
         * Carve a 3x3x3 pocket into a solid area (AutoMining).
         */
        CARVE_POCKET,
        /**
         * Use an already existing 3x3x3 space on the surface without modifying terrain (AutoSand).
         */
        EXISTING_SPACE
    }

    /**
     * Host callbacks. The controller never talks to the language table directly.
     */
    public interface Host {
        Setup storageSetup();

        Predicate<ItemStack> lootFilter();

        int storageMinY();

        long clickDelayMillis();

        boolean rotatePlacement();

        /**
         * @return whether a silk touch pickaxe is available, so a placed ender chest can be mined back
         * without consuming it
         */
        boolean canReclaimEnderChest();

        /**
         * Called once after the pocket was carved, before the box is placed. Returning false pauses the
         * controller in {@link Status#PREPARING}; the host must call {@link #resumeStorage()} later.
         */
        boolean prepareStorage();

        void notifyKey(String key, Object... args);

        void debugKey(String key, Object... args);
    }

    private enum Phase {
        IDLE,
        FINDING_SAFE_SPOT,
        PATHING_TO_SAFE_SPOT,
        CLEARING_AREA,
        PREPARING,
        PLACING_SHULKER,
        USING_SHULKER,
        PLACING_ENDER_CHEST,
        PAUSED_FOR_SHULKER,
        PAUSED_FOR_ENDER_CHEST,
        PAUSED_FOR_SILK,
        USING_ENDER_CHEST,
        RECLAIMING,
        PICKING_UP_ITEM,
        WAITING,
        DONE,
        FAILED
    }

    private static final int SCREEN_SETTLE_TICKS = 10;
    /**
     * Ticks between two container-open requests while the screen has not appeared yet. Sending one
     * every tick floods the server with use packets that can reopen the menu and desync the clicks.
     */
    private static final int OPEN_RETRY_TICKS = 10;
    /**
     * Ticks between two bulk storage passes, long enough for the server to echo the menu contents.
     */
    private static final int BULK_SETTLE_TICKS = 3;
    /**
     * Upper bound of quick-move clicks in one bulk pass (one per inventory slot).
     */
    private static final int MAX_BULK_CLICKS = ContainerInventoryMath.INVENTORY_SIZE;

    private final Host host;
    private final BaritoneBlockTask blockTask;
    private final ElapsedTimer actionTimer = new ElapsedTimer();
    private final List<BlockPos> reclaimPositions = new ArrayList<>();
    private final List<BlockPos> queuedReclaims = new ArrayList<>();

    private Phase phase = Phase.IDLE;
    private Phase resumePhase = Phase.IDLE;
    private int stateWaitTicks;
    private int pathTimeoutTicks;
    private int pathAttempts;
    private int openContainerTicks;
    private int openRetryTicks;
    private int screenSettleTicks;
    private int clearRounds;
    private int areaClearTicks;
    private int pickupBaseCount;
    private int pickupExpectedCount;
    private boolean areaClearStarted;
    private boolean areaClearSawActive;
    private boolean managedScreen;
    private BlockPos safeSpot;
    private BlockPos placedPos;
    private BlockPos reclaimSilkPos;
    private BlockPos standingTarget;
    private String failureKey = "";

    public StorageChainController(Host host, BaritoneBlockTask blockTask) {
        this.host = host;
        this.blockTask = blockTask;
    }

    // ================= public API =================

    public void beginStore() {
        this.begin();
    }

    public void resumeStorage() {
        if (this.phase == Phase.PREPARING) {
            this.phase = Phase.PLACING_SHULKER;
        }
    }

    public Status tick() {
        if (this.phase == Phase.IDLE) {
            return Status.IDLE;
        }
        if (this.phase == Phase.DONE) {
            return Status.DONE;
        }
        if (this.phase == Phase.FAILED) {
            return Status.FAILED;
        }
        switch (this.phase) {
            case FINDING_SAFE_SPOT -> this.findingSafeSpot();
            case PATHING_TO_SAFE_SPOT -> this.pathingToSafeSpot();
            case CLEARING_AREA -> this.clearingArea();
            case PLACING_SHULKER -> this.placingShulker();
            case USING_SHULKER -> this.usingShulker();
            case PLACING_ENDER_CHEST -> this.placingEnderChest();
            case PAUSED_FOR_SHULKER -> this.pausedForShulker();
            case PAUSED_FOR_ENDER_CHEST -> this.pausedForEnderChest();
            case PAUSED_FOR_SILK -> this.pausedForSilk();
            case USING_ENDER_CHEST -> this.usingEnderChest();
            case RECLAIMING -> this.reclaiming();
            case PICKING_UP_ITEM -> this.pickingUpPlacedBlocks();
            case WAITING -> this.waiting();
            default -> {
            }
        }
        return switch (this.phase) {
            case DONE -> Status.DONE;
            case FAILED -> Status.FAILED;
            case PREPARING -> Status.PREPARING;
            case PAUSED_FOR_SHULKER, PAUSED_FOR_ENDER_CHEST, PAUSED_FOR_SILK -> Status.PAUSED;
            default -> Status.RUNNING;
        };
    }

    public void cancel() {
        if (this.managedScreen) {
            ContainerInteractionUtils.closeScreen();
            this.managedScreen = false;
        }
        this.blockTask.cancel();
        ClientContext.baritoneBridge.cancelPathing();
        this.reset();
    }

    public boolean hasManagedScreen() {
        return this.managedScreen;
    }

    public BlockPos getSafeSpot() {
        return this.safeSpot;
    }

    public String failureKey() {
        return this.failureKey;
    }

    public void queueReclaim(BlockPos position) {
        if (position != null) {
            this.queuedReclaims.add(position);
        }
    }

    public boolean ensureStandingAtSpot() {
        if (this.safeSpot == null) {
            return false;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (this.host.storageSetup() == Setup.CARVE_POCKET) {
            BlockPos standPos = this.safeSpot.below();
            if (player.blockPosition().distSqr(standPos) <= StoragePlacement.STAND_DISTANCE_SQR) {
                return true;
            }
            if (!ClientContext.baritoneBridge.isPathingOrCalculating() || !standPos.equals(this.standingTarget)) {
                this.standingTarget = standPos;
                ClientContext.baritoneBridge.pathTo(standPos);
            }
            return false;
        }
        if (player.blockPosition().distSqr(this.safeSpot) <= StoragePlacement.SURFACE_STAND_DISTANCE_SQR) {
            return true;
        }
        if (!ClientContext.baritoneBridge.isPathingOrCalculating() || !this.safeSpot.equals(this.standingTarget)) {
            this.standingTarget = this.safeSpot;
            ClientContext.baritoneBridge.pathTo(this.safeSpot);
        }
        return false;
    }

    // ================= state machine =================

    private void begin() {
        if (this.managedScreen) {
            ContainerInteractionUtils.closeScreen();
        }
        this.blockTask.cancel();
        this.reset();
        this.phase = Phase.FINDING_SAFE_SPOT;
        this.actionTimer.reset();
    }

    private void findingSafeSpot() {
        if (this.safeSpot == null) {
            this.safeSpot = this.host.storageSetup() == Setup.CARVE_POCKET
                    ? StoragePlacement.findSafeSpot(this.host.storageMinY())
                    : StoragePlacement.findSurfaceSpot(evernightMinecraft.player.blockPosition(),
                    StoragePlacement.SURFACE_SPOT_RADIUS);
            if (this.safeSpot == null) {
                if (++this.pathAttempts >= 3) {
                    this.fail("NoSafeSpot");
                    return;
                }
                this.host.notifyKey("NoSafeSpot");
                this.waitTicks(Phase.FINDING_SAFE_SPOT, 40);
                return;
            }
            this.pathAttempts = 0;
            this.standingTarget = null;
        }
        LocalPlayer player = evernightMinecraft.player;
        boolean arrived = this.host.storageSetup() == Setup.CARVE_POCKET
                ? player.blockPosition().distSqr(this.safeSpot) < 4.0D
                : player.blockPosition().distSqr(this.safeSpot) <= StoragePlacement.SURFACE_STAND_DISTANCE_SQR;
        if (arrived) {
            this.arriveAtSpot();
            return;
        }
        this.pathTimeoutTicks = 0;
        this.phase = Phase.PATHING_TO_SAFE_SPOT;
    }

    private void pathingToSafeSpot() {
        if (this.safeSpot == null) {
            this.phase = Phase.FINDING_SAFE_SPOT;
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        boolean arrived = this.host.storageSetup() == Setup.CARVE_POCKET
                ? player.blockPosition().distSqr(this.safeSpot) < 4.0D
                : player.blockPosition().distSqr(this.safeSpot) <= StoragePlacement.SURFACE_STAND_DISTANCE_SQR;
        if (arrived) {
            ClientContext.baritoneBridge.cancelPathing();
            this.arriveAtSpot();
            return;
        }
        if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
            ClientContext.baritoneBridge.pathTo(this.safeSpot);
            this.pathTimeoutTicks = 0;
            return;
        }
        if (++this.pathTimeoutTicks > ContainerInventoryMath.PATH_TIMEOUT_TICKS) {
            ClientContext.baritoneBridge.cancelPathing();
            this.pathTimeoutTicks = 0;
            this.safeSpot = null;
            this.phase = Phase.FINDING_SAFE_SPOT;
        }
    }

    private void arriveAtSpot() {
        if (this.host.storageSetup() == Setup.EXISTING_SPACE) {
            this.phase = Phase.PLACING_SHULKER;
            return;
        }
        this.areaClearStarted = false;
        this.areaClearSawActive = false;
        this.areaClearTicks = 0;
        this.phase = Phase.CLEARING_AREA;
    }

    private void clearingArea() {
        if (this.safeSpot == null) {
            this.phase = Phase.FINDING_SAFE_SPOT;
            return;
        }
        if (!this.areaClearStarted) {
            this.areaClearStarted = true;
            this.areaClearSawActive = false;
            this.areaClearTicks = 0;
            this.host.debugKey("ClearingDebug", String.valueOf(this.countPocketBlocks()));
            ClientContext.baritoneBridge.clearArea(
                    this.safeSpot.offset(-1, -1, -1),
                    this.safeSpot.offset(1, 1, 1));
            return;
        }
        if (ClientContext.baritoneBridge.isBuilding()) {
            this.areaClearSawActive = true;
            if (++this.areaClearTicks <= ContainerInventoryMath.AREA_CLEAR_TIMEOUT_TICKS) {
                return;
            }
        } else if (!this.areaClearSawActive && ++this.areaClearTicks <= 10) {
            // the builder process is registered on a later Baritone tick, do not fall through too early
            return;
        }
        ClientContext.baritoneBridge.cancelPathing();
        this.areaClearStarted = false;
        this.areaClearSawActive = false;
        if (this.countPocketBlocks() > 0 && this.clearRounds < 2) {
            this.clearRounds++;
            return;
        }
        this.clearRounds = 0;
        if (!this.host.prepareStorage()) {
            this.phase = Phase.PREPARING;
            return;
        }
        this.phase = Phase.PLACING_SHULKER;
    }

    private void placingShulker() {
        if (this.safeSpot == null) {
            this.phase = Phase.FINDING_SAFE_SPOT;
            return;
        }
        if (!this.blockTask.isActive()) {
            if (!this.ensureStandingAtSpot()) {
                return;
            }
            int boxSlot = ShulkerBoxUtils.findStorageBoxSlot(this.host.lootFilter());
            if (boxSlot == -1) {
                if (ShulkerBoxUtils.findFullLootBoxSlot(this.host.lootFilter()) != -1) {
                    // The chest phase pauses with a prompt when the ender chest or the silk touch
                    // pickaxe is missing, so the player can add one without losing the chest.
                    this.phase = Phase.PLACING_ENDER_CHEST;
                    return;
                }
                this.host.notifyKey("NoEmptyShulker");
                this.phase = Phase.PAUSED_FOR_SHULKER;
                return;
            }
            BlockPos target = StoragePlacement.findPlacePosition(this.safeSpot);
            if (target == null || !this.blockTask.startPlace(target, boxSlot)) {
                this.fail("NoPlaceSpot");
                return;
            }
            this.placedPos = target;
            return;
        }
        switch (this.blockTask.tick()) {
            case RUNNING, IDLE -> {
                return;
            }
            case FAILED -> {
                this.placedPos = null;
                this.fail("NoPlaceSpot");
                return;
            }
            case DONE -> {
            }
        }
        this.host.debugKey("ShulkerPlaced");
        this.waitTicks(Phase.USING_SHULKER, 10);
    }

    private void usingShulker() {
        if (this.placedPos == null) {
            this.beginReclaim();
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        Screen screen = evernightMinecraft.screen;
        if (!(screen instanceof ShulkerBoxScreen) || !(player.containerMenu instanceof ShulkerBoxMenu)) {
            ContainerInteractionUtils.closeForeignScreen(ShulkerBoxScreen.class);
            this.screenSettleTicks = 0;
            if (++this.openContainerTicks >= ContainerInventoryMath.OPEN_CONTAINER_TIMEOUT_TICKS) {
                this.fail("OpenTimeout");
                return;
            }
            if (this.openRetryTicks > 0) {
                this.openRetryTicks--;
                return;
            }
            ContainerInteractionUtils.openContainer(this.placedPos, this.host.rotatePlacement());
            this.managedScreen = true;
            this.openRetryTicks = OPEN_RETRY_TICKS;
            return;
        }
        this.openContainerTicks = 0;
        this.openRetryTicks = 0;
        // The client menu exists before the server sends its contents; clicking too early hits empty
        // slots and the server resyncs, so give the content packet a few ticks to arrive.
        if (this.screenSettleTicks < SCREEN_SETTLE_TICKS) {
            this.screenSettleTicks++;
            return;
        }
        if (!this.actionTimer.hasElapsed(this.host.clickDelayMillis())) {
            return;
        }
        this.storeIntoTrackedBox(player.containerMenu);
    }

    /**
     * Moves every loot stack in the inventory into the tracked box in one tick instead of one stack
     * per click delay. Clicks beyond the box's free slots are no-ops on the server, and the next tick
     * sees the predicted contents and either continues or closes a full box.
     */
    private void storeIntoTrackedBox(AbstractContainerMenu menu) {
        if (ShulkerBoxUtils.isShulkerFull(menu)) {
            this.host.notifyKey("ShulkerFull");
            this.closeManagedScreen();
            this.beginReclaim();
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        Inventory inventory = player.getInventory();
        Predicate<ItemStack> lootFilter = this.host.lootFilter();
        int moved = 0;
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE && moved < MAX_BULK_CLICKS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !lootFilter.test(stack)) {
                continue;
            }
            int menuSlot = ContainerInventoryMath.inventorySlotToMenuSlot(slot,
                    ContainerInventoryMath.MenuKind.CONTAINER);
            if (menuSlot < 0) {
                continue;
            }
            InventoryUtils.clickSlot(menu.containerId, menuSlot, 0, ClickType.QUICK_MOVE);
            moved++;
        }
        if (moved > 0) {
            this.actionTimer.reset();
            this.waitTicks(Phase.USING_SHULKER, BULK_SETTLE_TICKS);
            return;
        }
        this.closeManagedScreen();
        this.beginReclaim();
    }

    private void placingEnderChest() {
        if (this.findEnderChestSlot() == -1) {
            this.host.notifyKey("NoEnderChest");
            this.phase = Phase.PAUSED_FOR_ENDER_CHEST;
            return;
        }
        if (!this.host.canReclaimEnderChest()) {
            // Pause instead of failing: the player can add a silk touch pickaxe and the chain
            // continues from here, so the ender chest is never consumed.
            this.host.notifyKey("NoSilkTouch");
            this.phase = Phase.PAUSED_FOR_SILK;
            return;
        }
        if (this.safeSpot == null) {
            this.phase = Phase.FINDING_SAFE_SPOT;
            return;
        }
        if (!this.blockTask.isActive()) {
            if (!this.ensureStandingAtSpot()) {
                return;
            }
            int slot = this.findEnderChestSlot();
            if (slot == -1) {
                this.fail("NoEnderChest");
                return;
            }
            BlockPos target = StoragePlacement.findPlacePosition(this.safeSpot);
            if (target == null || !this.blockTask.startPlace(target, slot)) {
                this.fail("NoPlaceSpot");
                return;
            }
            this.placedPos = target;
            return;
        }
        switch (this.blockTask.tick()) {
            case RUNNING, IDLE -> {
                return;
            }
            case FAILED -> {
                this.placedPos = null;
                this.fail("NoPlaceSpot");
                return;
            }
            case DONE -> {
            }
        }
        this.waitTicks(Phase.USING_ENDER_CHEST, 10);
    }

    /**
     * Holds the storage until a usable shulker box shows up again; a full loot box moves the chain on
     * to the ender chest swap instead.
     */
    private void pausedForShulker() {
        if (ShulkerBoxUtils.findStorageBoxSlot(this.host.lootFilter()) != -1) {
            this.phase = Phase.PLACING_SHULKER;
        } else if (ShulkerBoxUtils.findFullLootBoxSlot(this.host.lootFilter()) != -1) {
            this.phase = Phase.PLACING_ENDER_CHEST;
        }
    }

    /**
     * Holds the swap until an ender chest shows up in the inventory again; the chain then re-checks
     * the silk touch pickaxe and continues where it stopped.
     */
    private void pausedForEnderChest() {
        if (this.findEnderChestSlot() != -1) {
            this.phase = Phase.PLACING_ENDER_CHEST;
        }
    }

    /**
     * Holds the swap until a silk touch pickaxe shows up again; the chain then continues where it
     * stopped, so a player who picks one up does not have to restart the storage cycle.
     */
    private void pausedForSilk() {
        if (this.host.canReclaimEnderChest()) {
            this.phase = Phase.PLACING_ENDER_CHEST;
        }
    }

    private void usingEnderChest() {
        if (this.placedPos == null) {
            this.beginReclaim();
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        Screen screen = evernightMinecraft.screen;
        if (!(screen instanceof ContainerScreen) || !(player.containerMenu instanceof ChestMenu)) {
            ContainerInteractionUtils.closeForeignScreen(ContainerScreen.class);
            this.screenSettleTicks = 0;
            if (++this.openContainerTicks >= ContainerInventoryMath.OPEN_CONTAINER_TIMEOUT_TICKS) {
                this.fail("OpenTimeout");
                return;
            }
            if (this.openRetryTicks > 0) {
                this.openRetryTicks--;
                return;
            }
            ContainerInteractionUtils.openContainer(this.placedPos, this.host.rotatePlacement());
            this.managedScreen = true;
            this.openRetryTicks = OPEN_RETRY_TICKS;
            return;
        }
        this.openContainerTicks = 0;
        this.openRetryTicks = 0;
        if (this.screenSettleTicks < SCREEN_SETTLE_TICKS) {
            this.screenSettleTicks++;
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!this.actionTimer.hasElapsed(this.host.clickDelayMillis())) {
            return;
        }
        this.storeInEnderChest(menu);
    }

    private void storeInEnderChest(AbstractContainerMenu menu) {
        int fullBoxSlot = ShulkerBoxUtils.findFullLootBoxSlot(this.host.lootFilter());
        if (fullBoxSlot != -1) {
            int inventorySlot = ContainerInventoryMath.inventorySlotToMenuSlot(fullBoxSlot,
                    ContainerInventoryMath.MenuKind.CONTAINER);
            int emptySlot = ShulkerBoxUtils.findEmptyContainerSlot(menu, 0, ContainerInventoryMath.CONTAINER_SLOTS - 1);
            if (emptySlot != -1 && inventorySlot >= 0) {
                InventoryUtils.clickSlot(menu.containerId, inventorySlot, 0, ClickType.PICKUP);
                InventoryUtils.clickSlot(menu.containerId, emptySlot, 0, ClickType.PICKUP);
                this.host.debugKey("EnderChestDeposit");
                this.actionTimer.reset();
                this.waitTicks(Phase.USING_ENDER_CHEST, 4);
                return;
            }
            int emptyBoxInChest = ShulkerBoxUtils.findTakableShulkerInMenu(menu, this.host.lootFilter());
            if (emptyBoxInChest != -1 && inventorySlot >= 0) {
                // Swap the full box for the empty one, then put the empty box into the slot the full
                // box just left; without the third click the box stays on the cursor and the chain
                // cannot continue.
                InventoryUtils.clickSlot(menu.containerId, inventorySlot, 0, ClickType.PICKUP);
                InventoryUtils.clickSlot(menu.containerId, emptyBoxInChest, 0, ClickType.PICKUP);
                InventoryUtils.clickSlot(menu.containerId, inventorySlot, 0, ClickType.PICKUP);
                this.host.debugKey("EnderChestDeposit");
                this.closeManagedScreen();
                this.beginReclaim();
                return;
            }
            this.fail("EnderChestFull");
            return;
        }
        int boxSlot = ShulkerBoxUtils.findTakableShulkerInMenu(menu, this.host.lootFilter());
        if (boxSlot != -1) {
            if (this.hasEmptyMainSlot()) {
                InventoryUtils.clickSlot(menu.containerId, boxSlot, 0, ClickType.QUICK_MOVE);
            } else if (!this.tradeBoxForItem(menu, boxSlot)) {
                this.fail("EnderChestNoBox");
                return;
            }
            this.closeManagedScreen();
            this.beginReclaim();
            return;
        }
        this.fail("EnderChestNoBox");
    }

    private void beginReclaim() {
        this.reclaimPositions.clear();
        if (this.placedPos != null) {
            this.reclaimPositions.add(this.placedPos);
            this.placedPos = null;
        }
        this.reclaimPositions.addAll(this.queuedReclaims);
        this.queuedReclaims.clear();
        this.reclaimSilkPos = null;
        for (BlockPos position : this.reclaimPositions) {
            if (this.isBlockAt(position, Blocks.ENDER_CHEST)) {
                this.reclaimSilkPos = position;
                break;
            }
        }
        this.phase = this.reclaimPositions.isEmpty() ? Phase.DONE : Phase.RECLAIMING;
    }

    private void reclaiming() {
        if (!this.blockTask.isActive()) {
            if (this.reclaimSilkPos != null) {
                this.blockTask.startBreak(this.reclaimSilkPos);
                return;
            }
            if (!this.reclaimPositions.isEmpty()) {
                this.blockTask.startBreakArea(
                        PlacedBlockReclaim.corner(this.reclaimPositions, false),
                        PlacedBlockReclaim.corner(this.reclaimPositions, true),
                        this.reclaimPositions);
                return;
            }
            this.finishReclaim();
            return;
        }
        BaritoneBlockTask.Phase taskPhase = this.blockTask.tick();
        if (taskPhase == BaritoneBlockTask.Phase.RUNNING) {
            return;
        }
        if (taskPhase != BaritoneBlockTask.Phase.DONE) {
            this.host.debugKey("BreakFailed");
        }
        if (this.reclaimSilkPos != null) {
            // the chest is gone now; keep it in the list so its drop is part of the pickup count
            this.reclaimSilkPos = null;
            return;
        }
        int expectedPickups = this.reclaimPositions.size();
        this.reclaimPositions.clear();
        this.startPlacedBlockPickup(expectedPickups);
    }

    private void finishReclaim() {
        ClientContext.baritoneBridge.cancelPathing();
        this.reclaimPositions.clear();
        this.reclaimSilkPos = null;
        this.phase = Phase.DONE;
    }

    private void startPlacedBlockPickup(int expectedPickups) {
        this.pickupBaseCount = PlacedBlockReclaim.countPlacedBlockItems();
        this.pickupExpectedCount = PlacedBlockReclaim.expectedPickups(expectedPickups);
        this.pathTimeoutTicks = 0;
        this.actionTimer.reset();
        this.phase = Phase.PICKING_UP_ITEM;
    }

    private void pickingUpPlacedBlocks() {
        if (PlacedBlockReclaim.allCollected(PlacedBlockReclaim.countPlacedBlockItems(),
                this.pickupBaseCount, this.pickupExpectedCount)) {
            this.completePlacedBlockPickup(true);
            return;
        }
        ClientContext.baritoneBridge.pickupItems(stack -> PlacedBlockReclaim.matchesPlacedBlock(stack.getItem()));
        if (++this.pathTimeoutTicks <= ContainerInventoryMath.PICKUP_TIMEOUT_TICKS) {
            return;
        }
        this.pathTimeoutTicks = 0;
        this.completePlacedBlockPickup(false);
    }

    private void completePlacedBlockPickup(boolean pickedUp) {
        ClientContext.baritoneBridge.cancelMining();
        this.pathTimeoutTicks = 0;
        this.pickupBaseCount = 0;
        this.pickupExpectedCount = 0;
        if (!pickedUp) {
            this.host.debugKey("PickupTimeout");
        } else {
            this.host.debugKey("PickupDone");
        }
        this.phase = Phase.DONE;
    }

    private void waiting() {
        if (this.stateWaitTicks > 0) {
            this.stateWaitTicks--;
            return;
        }
        this.phase = this.resumePhase;
    }

    private void waitTicks(Phase resumePhase, int ticks) {
        this.resumePhase = resumePhase;
        this.stateWaitTicks = ticks;
        this.phase = Phase.WAITING;
    }

    private void fail(String key) {
        this.failureKey = key;
        this.phase = Phase.FAILED;
    }

    // ================= helpers =================

    private int countPocketBlocks() {
        int count = 0;
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos position = this.safeSpot.offset(dx, dy, dz);
                    if (!WorldUtils.isAir(position) && WorldUtils.canMineBlock(position)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean hasEmptyMainSlot() {
        return InventoryUtils.findInventorySlot(ItemStack::isEmpty, false) != -1;
    }

    private int findEnderChestSlot() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return -1;
        }
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            if (player.getInventory().getItem(slot).is(Items.ENDER_CHEST)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isBlockAt(BlockPos position, Block block) {
        return evernightMinecraft.level != null
                && evernightMinecraft.level.getBlockState(position).is(block);
    }

    /**
     * Swaps a stack of the loot out of the inventory for the box on the cursor, so a full inventory
     * can still take a box out of the ender chest.
     */
    private boolean tradeBoxForItem(AbstractContainerMenu menu, int boxSlot) {
        LocalPlayer player = evernightMinecraft.player;
        Inventory inventory = player.getInventory();
        boolean[] matches = new boolean[ContainerInventoryMath.INVENTORY_SIZE];
        Predicate<ItemStack> lootFilter = this.host.lootFilter();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            matches[slot] = lootFilter.test(inventory.getItem(slot));
        }
        int tradeSlot = ContainerInventoryMath.findTradeSlot(matches);
        if (tradeSlot == -1) {
            return false;
        }
        int menuSlot = ContainerInventoryMath.inventorySlotToMenuSlot(tradeSlot,
                ContainerInventoryMath.MenuKind.CONTAINER);
        if (menuSlot < 0) {
            return false;
        }
        InventoryUtils.clickSlot(menu.containerId, menuSlot, 0, ClickType.PICKUP);
        InventoryUtils.clickSlot(menu.containerId, boxSlot, 0, ClickType.PICKUP);
        return true;
    }

    private void closeManagedScreen() {
        ContainerInteractionUtils.closeScreen();
        this.managedScreen = false;
        this.screenSettleTicks = 0;
        this.openRetryTicks = 0;
    }

    private void reset() {
        this.phase = Phase.IDLE;
        this.resumePhase = Phase.IDLE;
        this.stateWaitTicks = 0;
        this.pathTimeoutTicks = 0;
        this.pathAttempts = 0;
        this.openContainerTicks = 0;
        this.openRetryTicks = 0;
        this.screenSettleTicks = 0;
        this.clearRounds = 0;
        this.areaClearTicks = 0;
        this.pickupBaseCount = 0;
        this.pickupExpectedCount = 0;
        this.areaClearStarted = false;
        this.areaClearSawActive = false;
        this.safeSpot = null;
        this.placedPos = null;
        this.reclaimSilkPos = null;
        this.standingTarget = null;
        this.reclaimPositions.clear();
        this.queuedReclaims.clear();
        this.failureKey = "";
        this.managedScreen = false;
    }
}
