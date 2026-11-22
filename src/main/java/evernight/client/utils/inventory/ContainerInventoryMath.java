package evernight.client.utils.inventory;

/**
 * Inventory and container math shared by the automatic mining modules.
 *
 * <p>Everything in here is pure: it only looks at slot counts and boolean flags, so the rules can be
 * reasoned about without a running client. Slot indices are always raw player inventory indices:
 * 0-8 hotbar, 9-35 main inventory.
 */
public final class ContainerInventoryMath {
    /**
     * Number of hotbar slots.
     */
    public static final int HOTBAR_SIZE = 9;
    /**
     * Number of player inventory slots including the hotbar.
     */
    public static final int INVENTORY_SIZE = 36;
    /**
     * Number of slots inside a shulker box / single chest / ender chest.
     */
    public static final int CONTAINER_SLOTS = 27;
    /**
     * Ticks to wait for a container screen before giving up (20 seconds).
     */
    public static final int OPEN_CONTAINER_TIMEOUT_TICKS = 400;
    /**
     * Ticks a pickup may take before the module gives up on it.
     */
    public static final int PICKUP_TIMEOUT_TICKS = 200;
    /**
     * Ticks without an active mine process before the scan is restarted.
     */
    public static final int RESTART_INTERVAL_TICKS = 100;
    /**
     * Ticks a path to the storage spot may take before the spot is dropped.
     */
    public static final int PATH_TIMEOUT_TICKS = 600;
    /**
     * Ticks Baritone may spend on carving the storage pocket before the module retries or gives up.
     */
    public static final int AREA_CLEAR_TIMEOUT_TICKS = 400;

    private ContainerInventoryMath() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * @return whether every main inventory slot (9-35) holds at least one item; the hotbar is ignored
     * on purpose so the module keeps a free hand for storage blocks
     */
    public static boolean isMainInventoryFull(int[] counts) {
        if (counts.length < INVENTORY_SIZE) {
            return false;
        }
        for (int slot = HOTBAR_SIZE; slot < INVENTORY_SIZE; slot++) {
            if (counts[slot] <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return whether the remaining durability of a tool is below the configured threshold
     */
    public static boolean isDurabilityLow(int maxDamage, int damage, int threshold) {
        if (maxDamage <= 0) {
            return false;
        }
        return maxDamage - damage < threshold;
    }

    /**
     * Translates a player inventory slot into the matching slot of the open container menu.
     *
     * @return the menu slot, or -1 when the inventory slot is out of range
     */
    public static int inventorySlotToMenuSlot(int inventorySlot, MenuKind kind) {
        if (inventorySlot < 0 || inventorySlot >= INVENTORY_SIZE) {
            return -1;
        }
        boolean hotbar = inventorySlot < HOTBAR_SIZE;
        return switch (kind) {
            case PLAYER -> hotbar ? inventorySlot + 36 : inventorySlot;
            case CRAFTING -> hotbar ? inventorySlot + 37 : inventorySlot + 1;
            case CONTAINER -> hotbar ? inventorySlot + 54 : inventorySlot + 18;
        };
    }

    /**
     * A shulker box counts as full only when all 27 slots hold a full stack; a partially filled last
     * stack keeps the box in use.
     */
    public static boolean isShulkerFull(int[] stackCounts, int[] maxCounts) {
        for (int slot = 0; slot < CONTAINER_SLOTS; slot++) {
            if (stackCounts[slot] <= 0) {
                return false;
            }
            if (stackCounts[slot] < maxCounts[slot]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the first shulker box that is completely filled with full stacks and contains the
     * target item, or -1
     */
    public static int findFullTargetBoxSlot(boolean[] fullStack, boolean[] containsTarget) {
        int length = Math.min(fullStack.length, containsTarget.length);
        for (int slot = 0; slot < length; slot++) {
            if (fullStack[slot] && containsTarget[slot]) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Picks the box to fill next: the box that already holds the most target items wins, an empty box
     * is the fallback.
     *
     * @return the chosen slot, or -1 when no box is usable
     */
    public static int pickStorageBox(int[] targetCounts, boolean[] isEmpty, boolean[] isAllTarget) {
        int bestSlot = -1;
        int bestCount = -1;
        for (int slot = 0; slot < targetCounts.length; slot++) {
            if (isAllTarget[slot] && targetCounts[slot] > bestCount) {
                bestCount = targetCounts[slot];
                bestSlot = slot;
            }
        }
        if (bestSlot != -1) {
            return bestSlot;
        }
        for (int slot = 0; slot < isEmpty.length; slot++) {
            if (isEmpty[slot]) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Picks a box that can be taken out of the ender chest to keep storing: an empty box or a box that
     * only holds the target item and is not full yet.
     */
    public static int pickTakableShulker(boolean[] isEmpty, boolean[] isAllTarget, int[] filledCounts) {
        for (int slot = 0; slot < isEmpty.length; slot++) {
            if (isEmpty[slot]) {
                return slot;
            }
            if (isAllTarget[slot] && filledCounts[slot] < CONTAINER_SLOTS) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return the first inventory slot holding the target item, used when the inventory is full and a
     * stack has to be traded for a shulker box
     */
    public static int findTradeSlot(boolean[] inventoryItemMatches) {
        for (int slot = 0; slot < inventoryItemMatches.length; slot++) {
            if (inventoryItemMatches[slot]) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return whether the applied Y band differs from the requested one
     */
    public static boolean isYRangeChanged(String appliedKey, int minY, int maxY) {
        return !(minY + "~" + maxY).equals(appliedKey);
    }

    /**
     * Baritone stops mining once its process finds nothing to do; restarting the scan while a mine is
     * still running would interrupt it, so only restart when idle for long enough.
     */
    public static boolean shouldRestartMining(boolean miningActive, int elapsedTicks) {
        return !miningActive && elapsedTicks >= RESTART_INTERVAL_TICKS;
    }

    /**
     * Layout of the container that is currently open, used to translate inventory to menu slots.
     */
    public enum MenuKind {
        /**
         * The player's own inventory menu (hotbar sits behind armour and crafting slots).
         */
        PLAYER,
        /**
         * Crafting table menu: result slot plus a 3x3 grid in front of the player section.
         */
        CRAFTING,
        /**
         * 27 slot container such as a shulker box or an ender chest.
         */
        CONTAINER
    }
}
