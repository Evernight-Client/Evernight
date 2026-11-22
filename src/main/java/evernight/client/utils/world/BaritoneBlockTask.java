package evernight.client.utils.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.utils.inventory.ContainerInventoryMath;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One "place this block" or "break these blocks" job for the storage chain.
 *
 * <p>Both are handed to Baritone, and both are finished when the world state matches, never when the
 * builder process disappears: Baritone registers the process one tick later, cancels it when another
 * process takes over and can finish the work before we ever see it active. Every tick therefore checks
 *
 * <ol>
 *   <li>does the world state match? -> {@link Phase#DONE}</li>
 *   <li>is the builder still running? -> keep waiting</li>
 *   <li>builder is gone but the state did not change -> send the job again, a few times at most</li>
 * </ol>
 *
 * <p>and fails after a total timeout, so the state machine can never hang on a job.
 *
 * <p>The builder only takes stacks that sit on the hotbar (it never reads the main inventory), so a
 * placement job holds the block in the hand before it asks for the build and puts the displaced hotbar
 * stack back once the job is over.
 */
public final class BaritoneBlockTask implements ClientAccess {
    /**
     * Total ticks one job may take before it fails.
     */
    private static final int TIMEOUT_TICKS = 400;
    /**
     * Ticks a placed block has to survive before the job counts as done (the server may roll back).
     */
    private static final int PLACE_CONFIRM_TICKS = 5;
    /**
     * Ticks to wait for the builder process to show up before the job is resent.
     */
    private static final int RESEND_INTERVAL_TICKS = 40;
    /**
     * Upper bound on how often one job is sent.
     */
    private static final int MAX_SENDS = 4;
    private final List<BlockPos> targets = new ArrayList<>();
    private BlockPos firstCorner;
    private BlockPos secondCorner;
    private BlockState wantedState;
    private Block expectedBlock;
    private Item expectedItem;
    private boolean placing;
    private int confirmedTicks;
    private int ticks;
    private int sends;
    private int sourceSlot = -1;
    private int hotbarSlot = -1;
    private Item heldItem;
    private Item displacedItem;

    public boolean isActive() {
        return !this.targets.isEmpty();
    }

    /**
     * Queues a placement job for the block held by one player inventory slot.
     *
     * @param target     the position the block has to end up at
     * @param sourceSlot player inventory slot (hotbar or main inventory) holding the block
     * @return whether the stack can be placed at all
     */
    public boolean startPlace(BlockPos target, int sourceSlot) {
        this.reset();
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null || sourceSlot < 0 || sourceSlot >= ContainerInventoryMath.INVENTORY_SIZE) {
            return false;
        }
        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(sourceSlot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        this.sourceSlot = sourceSlot;
        this.expectedItem = blockItem;
        this.expectedBlock = blockItem.getBlock();
        this.wantedState = blockItem.getBlock().defaultBlockState();
        this.placing = true;
        this.firstCorner = target;
        this.secondCorner = target;
        this.targets.add(target);
        this.sends = 1;
        this.sendPlace();
        return true;
    }

    /**
     * Queues a break job for a single block.
     */
    public void startBreak(BlockPos target) {
        this.startBreakArea(target, target, List.of(target));
    }

    /**
     * Queues one break job covering several blocks. The corners may span more than the targets, which is
     * what lets Baritone clear the leftover blocks in a single run.
     */
    public void startBreakArea(BlockPos firstCorner, BlockPos secondCorner, Collection<BlockPos> targetsToClear) {
        this.reset();
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
        this.targets.addAll(targetsToClear);
        this.placing = false;
        this.sends = 1;
        ClientContext.baritoneBridge.clearArea(firstCorner, secondCorner);
    }

    /**
     * Advances the job by one tick.
     */
    public Phase tick() {
        if (this.targets.isEmpty()) {
            return Phase.IDLE;
        }
        return this.placing ? this.tickPlacement() : this.tickBreak();
    }

    /**
     * Aborts the job and removes any Baritone process it left behind.
     */
    public void cancel() {
        if (!this.targets.isEmpty()) {
            ClientContext.baritoneBridge.cancelPathing();
        }
        this.reset();
    }

    private Phase tickPlacement() {
        if (this.reached()) {
            if (++this.confirmedTicks >= PLACE_CONFIRM_TICKS) {
                this.finish();
                return Phase.DONE;
            }
            return Phase.RUNNING;
        }
        this.confirmedTicks = 0;
        if (!WorldUtils.isAir(this.firstCorner)) {
            // something else occupies the target now, this placement can never succeed
            this.finish();
            return Phase.FAILED;
        }
        if (++this.ticks > TIMEOUT_TICKS) {
            this.finish();
            return Phase.FAILED;
        }
        if (ClientContext.baritoneBridge.isBuilding()) {
            return Phase.RUNNING;
        }
        if (this.ticks < RESEND_INTERVAL_TICKS) {
            return Phase.RUNNING;
        }
        if (this.sends >= MAX_SENDS) {
            this.finish();
            return Phase.FAILED;
        }
        this.sends++;
        this.ticks = 0;
        this.sendPlace();
        return Phase.RUNNING;
    }

    private Phase tickBreak() {
        if (this.reached()) {
            this.finish();
            return Phase.DONE;
        }
        if (++this.ticks > TIMEOUT_TICKS) {
            this.finish();
            return Phase.FAILED;
        }
        if (ClientContext.baritoneBridge.isBuilding()) {
            return Phase.RUNNING;
        }
        if (this.ticks < RESEND_INTERVAL_TICKS) {
            return Phase.RUNNING;
        }
        if (this.sends >= MAX_SENDS) {
            this.finish();
            return Phase.FAILED;
        }
        this.sends++;
        this.ticks = 0;
        if (this.firstCorner != null && this.secondCorner != null) {
            ClientContext.baritoneBridge.clearArea(this.firstCorner, this.secondCorner);
        }
        return Phase.RUNNING;
    }

    /**
     * Hands the placement to Baritone, holding the block first: the builder only looks at the hotbar, so
     * a stack from the main inventory would never be found.
     *
     * @return whether the job was sent
     */
    private boolean sendPlace() {
        if (this.firstCorner == null || this.wantedState == null || !this.holdBlock()) {
            return false;
        }
        ClientContext.baritoneBridge.placeBlock(this.firstCorner, this.wantedState);
        return true;
    }

    /**
     * Puts the block stack into the hand: a stack from the main inventory is swapped into the held
     * hotbar slot, a stack that already sits on the hotbar is only selected. Both stacks are remembered
     * so {@link #restoreHotbar()} can undo the swap once the job is over.
     *
     * @return whether the block is held now
     */
    private boolean holdBlock() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null || this.expectedItem == null || this.sourceSlot < 0) {
            return false;
        }
        if (ClientAccess.evernightMinecraft.screen != null) {
            // a container is open: the inventory slot indices would point into its menu, never click there
            return false;
        }
        Inventory inventory = player.getInventory();
        if (this.hotbarSlot == -1) {
            if (this.sourceSlot < ContainerInventoryMath.HOTBAR_SIZE) {
                if (inventory.getSelectedSlot() != this.sourceSlot) {
                    InventoryUtils.selectHotbarSlot(this.sourceSlot);
                }
            } else {
                this.hotbarSlot = inventory.getSelectedSlot();
                this.heldItem = inventory.getItem(this.sourceSlot).getItem();
                this.displacedItem = inventory.getItem(this.hotbarSlot).getItem();
                InventoryUtils.swapIntoSelectedSlot(this.sourceSlot);
            }
        } else if (inventory.getSelectedSlot() != this.hotbarSlot) {
            InventoryUtils.selectHotbarSlot(this.hotbarSlot);
        }
        if (player.getMainHandItem().is(this.expectedItem)) {
            return true;
        }
        // A rejected swap is rolled back by the server: retry it while both slots still look untouched.
        if (this.sourceSlot >= ContainerInventoryMath.HOTBAR_SIZE
                && inventory.getItem(this.sourceSlot).is(this.expectedItem)
                && inventory.getItem(this.hotbarSlot).getItem() == this.displacedItem) {
            InventoryUtils.swapIntoSelectedSlot(this.sourceSlot);
            return player.getMainHandItem().is(this.expectedItem);
        }
        return false;
    }

    /**
     * Puts the displaced hotbar stack back where it came from. Skipped when either slot no longer holds
     * what the swap left there, so a player edit or a Baritone inventory move is never undone blindly.
     */
    private void restoreHotbar() {
        int hotbarSlot = this.hotbarSlot;
        int sourceSlot = this.sourceSlot;
        Item heldItem = this.heldItem;
        Item displacedItem = this.displacedItem;
        this.clearHotbarSwap();
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (hotbarSlot == -1 || sourceSlot == -1 || player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.getItem(hotbarSlot);
        if (!held.isEmpty() && held.getItem() != heldItem) {
            return;
        }
        if (inventory.getItem(sourceSlot).getItem() != displacedItem) {
            return;
        }
        InventoryUtils.swapWithHotbarSlot(InventoryUtils.toMenuSlot(sourceSlot), hotbarSlot);
    }

    private void clearHotbarSwap() {
        this.hotbarSlot = -1;
        this.heldItem = null;
        this.displacedItem = null;
    }

    private boolean reached() {
        if (this.targets.isEmpty() || ClientAccess.evernightMinecraft.level == null) {
            return false;
        }
        for (BlockPos target : this.targets) {
            if (this.placing) {
                if (!ClientAccess.evernightMinecraft.level.getBlockState(target).is(this.expectedBlock)) {
                    return false;
                }
            } else if (!WorldUtils.isAir(target)) {
                return false;
            }
        }
        return true;
    }

    private void finish() {
        ClientContext.baritoneBridge.cancelPathing();
        this.reset();
    }

    private void reset() {
        this.restoreHotbar();
        this.targets.clear();
        this.firstCorner = null;
        this.secondCorner = null;
        this.wantedState = null;
        this.expectedBlock = null;
        this.expectedItem = null;
        this.placing = false;
        this.confirmedTicks = 0;
        this.ticks = 0;
        this.sends = 0;
        this.sourceSlot = -1;
    }

    /**
     * Result of one tick of a job.
     */
    public enum Phase {
        /**
         * nothing queued
         */
        IDLE,
        /**
         * still working
         */
        RUNNING,
        /**
         * the world state matches
         */
        DONE,
        /**
         * gave up after the retries
         */
        FAILED
    }
}
