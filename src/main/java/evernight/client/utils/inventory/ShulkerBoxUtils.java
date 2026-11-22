package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.function.Predicate;

/**
 * Shulker box content lookups shared by the automatic mining modules.
 *
 * <p>All methods read the live local player and return -1 / false when nothing matches. The box
 * contents are read from the {@code CONTAINER} data component, so a box can be inspected without
 * opening it.
 */
public final class ShulkerBoxUtils implements ClientAccess {
    private ShulkerBoxUtils() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    public static boolean isShulkerBoxItem(Item item) {
        return ItemUtils.isShulkerBox(item);
    }

    /**
     * @return whether the box holds nothing but full stacks in all 27 slots
     */
    public static boolean isFullStack(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return false;
        }
        int filled = 0;
        for (ItemStack inner : contents.nonEmptyItems()) {
            filled++;
            if (inner.getCount() < inner.getMaxStackSize()) {
                return false;
            }
        }
        return filled >= ContainerInventoryMath.CONTAINER_SLOTS;
    }

    public static boolean hasShulkerBox() {
        if (evernightMinecraft.player == null) {
            return false;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            if (isShulkerBoxItem(inventory.getItem(slot).getItem())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks the box to fill with the loot, see {@link ContainerInventoryMath#pickStorageBox}.
     *
     * @return the inventory slot of the box, or -1 when the inventory has no usable box
     */
    public static int findStorageBoxSlot(Predicate<ItemStack> lootFilter) {
        if (evernightMinecraft.player == null) {
            return -1;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        int[] targetCounts = new int[ContainerInventoryMath.INVENTORY_SIZE];
        boolean[] isEmpty = new boolean[ContainerInventoryMath.INVENTORY_SIZE];
        boolean[] isAllTarget = new boolean[ContainerInventoryMath.INVENTORY_SIZE];
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isShulkerBoxItem(stack.getItem())) {
                continue;
            }
            if (isFullStack(stack)) {
                continue;
            }
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) {
                isEmpty[slot] = true;
                continue;
            }
            boolean allTarget = true;
            int count = 0;
            for (ItemStack inner : contents.nonEmptyItems()) {
                if (lootFilter.test(inner)) {
                    count += inner.getCount();
                } else {
                    allTarget = false;
                }
            }
            isAllTarget[slot] = allTarget;
            targetCounts[slot] = count;
        }
        return ContainerInventoryMath.pickStorageBox(targetCounts, isEmpty, isAllTarget);
    }

    /**
     * @return the inventory slot of a completely filled box that contains the loot, or -1
     */
    public static int findFullLootBoxSlot(Predicate<ItemStack> lootFilter) {
        if (evernightMinecraft.player == null) {
            return -1;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        boolean[] fullStack = new boolean[ContainerInventoryMath.INVENTORY_SIZE];
        boolean[] containsTarget = new boolean[ContainerInventoryMath.INVENTORY_SIZE];
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isShulkerBoxItem(stack.getItem())) {
                continue;
            }
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) {
                continue;
            }
            boolean allFull = true;
            boolean hasTarget = false;
            int filled = 0;
            for (ItemStack inner : contents.nonEmptyItems()) {
                filled++;
                if (inner.getCount() < inner.getMaxStackSize()) {
                    allFull = false;
                }
                if (lootFilter.test(inner)) {
                    hasTarget = true;
                }
            }
            if (filled >= ContainerInventoryMath.CONTAINER_SLOTS && allFull) {
                fullStack[slot] = true;
            }
            containsTarget[slot] = hasTarget;
        }
        return ContainerInventoryMath.findFullTargetBoxSlot(fullStack, containsTarget);
    }

    public static int findEmptyContainerSlot(AbstractContainerMenu menu, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return the container slot of a box that can keep being filled (empty, or loot-only and not
     * full yet), or -1
     */
    public static int findTakableShulkerInMenu(AbstractContainerMenu menu, Predicate<ItemStack> lootFilter) {
        boolean[] isEmpty = new boolean[ContainerInventoryMath.CONTAINER_SLOTS];
        boolean[] isAllTarget = new boolean[ContainerInventoryMath.CONTAINER_SLOTS];
        int[] filledCounts = new int[ContainerInventoryMath.CONTAINER_SLOTS];
        for (int slot = 0; slot < ContainerInventoryMath.CONTAINER_SLOTS; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!isShulkerBoxItem(stack.getItem())) {
                continue;
            }
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) {
                isEmpty[slot] = true;
                continue;
            }
            boolean allTarget = true;
            int filled = 0;
            for (ItemStack inner : contents.nonEmptyItems()) {
                filled++;
                if (!lootFilter.test(inner)) {
                    allTarget = false;
                    break;
                }
            }
            isAllTarget[slot] = allTarget;
            filledCounts[slot] = filled;
        }
        return ContainerInventoryMath.pickTakableShulker(isEmpty, isAllTarget, filledCounts);
    }

    public static boolean isShulkerFull(AbstractContainerMenu menu) {
        int[] counts = new int[ContainerInventoryMath.CONTAINER_SLOTS];
        int[] maxCounts = new int[ContainerInventoryMath.CONTAINER_SLOTS];
        for (int slot = 0; slot < ContainerInventoryMath.CONTAINER_SLOTS; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            counts[slot] = stack.getCount();
            maxCounts[slot] = stack.getMaxStackSize();
        }
        return ContainerInventoryMath.isShulkerFull(counts, maxCounts);
    }
}
