package evernight.client.utils.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Bookkeeping for the blocks the storage chain places and later recovers: what counts as a placed
 * block, how many are expected back and which box spans all of them.
 */
public final class PlacedBlockReclaim {
    private PlacedBlockReclaim() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * @return whether the item can be one of the blocks the storage chain placed (shulker box,
     * crafting table or ender chest)
     */
    public static boolean matchesPlacedBlock(Item item) {
        return ItemUtils.isShulkerBox(item)
                || item == Items.CRAFTING_TABLE
                || item == Items.ENDER_CHEST;
    }

    /**
     * @return how many placed-block items the inventory currently holds
     */
    public static int countPlacedBlockItems() {
        return InventoryUtils.countItems(stack -> matchesPlacedBlock(stack.getItem()));
    }

    /**
     * @return the smallest or largest corner around every position, or null when the list is empty
     */
    public static BlockPos corner(List<BlockPos> positions, boolean largest) {
        if (positions.isEmpty()) {
            return null;
        }
        BlockPos first = positions.getFirst();
        int x = first.getX();
        int y = first.getY();
        int z = first.getZ();
        for (BlockPos position : positions) {
            x = largest ? Math.max(x, position.getX()) : Math.min(x, position.getX());
            y = largest ? Math.max(y, position.getY()) : Math.min(y, position.getY());
            z = largest ? Math.max(z, position.getZ()) : Math.min(z, position.getZ());
        }
        return new BlockPos(x, y, z);
    }

    /**
     * @return how many items have to be picked up before the reclaim is done
     */
    public static int expectedPickups(int placedCount) {
        return Math.max(1, placedCount);
    }

    /**
     * @return whether enough placed-block items were collected again
     */
    public static boolean allCollected(int currentCount, int baseCount, int expectedCount) {
        return currentCount - baseCount >= expectedCount;
    }
}
