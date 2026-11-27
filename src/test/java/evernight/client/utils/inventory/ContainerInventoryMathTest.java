package evernight.client.utils.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerInventoryMathTest {
    @Test
    @DisplayName("PLAYER/CRAFTING/CONTAINER 三套槽位映射逐槽正确")
    void menuSlotMappingIsExact() {
        for (int slot = 0; slot < ContainerInventoryMath.HOTBAR_SIZE; slot++) {
            assertEquals(slot + 36, ContainerInventoryMath.inventorySlotToMenuSlot(
                    slot, ContainerInventoryMath.MenuKind.PLAYER));
            assertEquals(slot + 37, ContainerInventoryMath.inventorySlotToMenuSlot(
                    slot, ContainerInventoryMath.MenuKind.CRAFTING));
            assertEquals(slot + 54, ContainerInventoryMath.inventorySlotToMenuSlot(
                    slot, ContainerInventoryMath.MenuKind.CONTAINER));
        }
        for (int slot = ContainerInventoryMath.HOTBAR_SIZE;
             slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            assertEquals(slot, ContainerInventoryMath.inventorySlotToMenuSlot(
                    slot, ContainerInventoryMath.MenuKind.PLAYER));
            assertEquals(slot + 1, ContainerInventoryMath.inventorySlotToMenuSlot(
                    slot, ContainerInventoryMath.MenuKind.CRAFTING));
            assertEquals(slot + 18, ContainerInventoryMath.inventorySlotToMenuSlot(
                    slot, ContainerInventoryMath.MenuKind.CONTAINER));
        }
        assertEquals(-1, ContainerInventoryMath.inventorySlotToMenuSlot(
                -1, ContainerInventoryMath.MenuKind.PLAYER));
        assertEquals(-1, ContainerInventoryMath.inventorySlotToMenuSlot(
                ContainerInventoryMath.INVENTORY_SIZE, ContainerInventoryMath.MenuKind.CONTAINER));
    }

    @Test
    @DisplayName("主背包满判断忽略 hotbar，长度不足返回 false")
    void mainInventoryFullIgnoresHotbar() {
        int[] counts = new int[ContainerInventoryMath.INVENTORY_SIZE];
        for (int slot = ContainerInventoryMath.HOTBAR_SIZE;
             slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            counts[slot] = 1;
        }
        assertTrue(ContainerInventoryMath.isMainInventoryFull(counts));

        counts[0] = 0;
        assertTrue(ContainerInventoryMath.isMainInventoryFull(counts),
                "hotbar slots are intentionally ignored");

        counts[20] = 0;
        assertFalse(ContainerInventoryMath.isMainInventoryFull(counts));
        assertFalse(ContainerInventoryMath.isMainInventoryFull(new int[ContainerInventoryMath.INVENTORY_SIZE - 1]));
    }

    @Test
    @DisplayName("耐久阈值使用严格小于，非正最大耐久不判低")
    void durabilityThresholdIsStrict() {
        assertFalse(ContainerInventoryMath.isDurabilityLow(0, 0, 10));
        assertFalse(ContainerInventoryMath.isDurabilityLow(-1, 0, 10));
        assertTrue(ContainerInventoryMath.isDurabilityLow(100, 90, 11));
        assertFalse(ContainerInventoryMath.isDurabilityLow(100, 90, 10));
        assertTrue(ContainerInventoryMath.isDurabilityLow(100, 100, 1));
        assertTrue(ContainerInventoryMath.isDurabilityLow(10, 11, 1));
    }

    @Test
    @DisplayName("潜影盒只有 27 格全满且每格满堆叠才算满")
    void shulkerFullRequiresEveryFullStack() {
        int[] counts = new int[ContainerInventoryMath.CONTAINER_SLOTS];
        int[] maximums = new int[ContainerInventoryMath.CONTAINER_SLOTS];
        for (int slot = 0; slot < ContainerInventoryMath.CONTAINER_SLOTS; slot++) {
            counts[slot] = 64;
            maximums[slot] = 64;
        }
        assertTrue(ContainerInventoryMath.isShulkerFull(counts, maximums));

        counts[26] = 63;
        assertFalse(ContainerInventoryMath.isShulkerFull(counts, maximums));

        counts[26] = 0;
        assertFalse(ContainerInventoryMath.isShulkerFull(counts, maximums));

        counts[26] = 64;
        counts[0] = 63;
        assertFalse(ContainerInventoryMath.isShulkerFull(counts, maximums));
    }

    @Test
    @DisplayName("满目标盒选择首个满足项，长度取两者较小值")
    void fullTargetBoxSelectionIsFirstMatch() {
        boolean[] full = {false, true, true};
        boolean[] contains = {true, false, true};
        assertEquals(2, ContainerInventoryMath.findFullTargetBoxSlot(full, contains));
        assertEquals(-1, ContainerInventoryMath.findFullTargetBoxSlot(
                new boolean[] {true}, new boolean[] {false}));
        assertEquals(-1, ContainerInventoryMath.findFullTargetBoxSlot(new boolean[0], new boolean[0]));
    }

    @Test
    @DisplayName("存盒优先目标数量最多的盒，否则退回第一个空盒")
    void storageBoxPrefersTargetThenEmpty() {
        int[] counts = {0, 3, 5, 1};
        boolean[] empty = {false, false, false, true};
        boolean[] allTarget = {true, true, true, false};
        assertEquals(2, ContainerInventoryMath.pickStorageBox(counts, empty, allTarget));

        boolean[] noTarget = {false, false, false, false};
        assertEquals(3, ContainerInventoryMath.pickStorageBox(counts, empty, noTarget));
        assertEquals(-1, ContainerInventoryMath.pickStorageBox(
                counts, new boolean[4], noTarget));
    }

    @Test
    @DisplayName("可取盒优先空盒，其次未满的目标盒")
    void takableShulkerPrefersEmptyThenPartialTarget() {
        boolean[] empty = {false, false, false};
        boolean[] allTarget = {false, true, true};
        int[] filled = {27, 26, 5};
        assertEquals(1, ContainerInventoryMath.pickTakableShulker(empty, allTarget, filled));

        empty[0] = true;
        assertEquals(0, ContainerInventoryMath.pickTakableShulker(empty, allTarget, filled));

        boolean[] fullTarget = {false, false};
        boolean[] target = {true, true};
        assertEquals(-1, ContainerInventoryMath.pickTakableShulker(
                fullTarget, target, new int[] {27, 27}));
    }

    @Test
    @DisplayName("交易槽取首个匹配，空数组返回 -1")
    void tradeSlotFindsFirstMatch() {
        assertEquals(0, ContainerInventoryMath.findTradeSlot(new boolean[] {true, true}));
        assertEquals(2, ContainerInventoryMath.findTradeSlot(new boolean[] {false, false, true}));
        assertEquals(-1, ContainerInventoryMath.findTradeSlot(new boolean[0]));
        assertEquals(-1, ContainerInventoryMath.findTradeSlot(new boolean[4]));
    }

    @Test
    @DisplayName("Y 范围与重启判断按字符串/100 tick 边界")
    void yRangeAndRestartBoundaries() {
        assertFalse(ContainerInventoryMath.isYRangeChanged("-64~320", -64, 320));
        assertTrue(ContainerInventoryMath.isYRangeChanged("-64~319", -64, 320));
        assertTrue(ContainerInventoryMath.isYRangeChanged(null, -64, 320));

        assertFalse(ContainerInventoryMath.shouldRestartMining(true,
                ContainerInventoryMath.RESTART_INTERVAL_TICKS));
        assertFalse(ContainerInventoryMath.shouldRestartMining(false,
                ContainerInventoryMath.RESTART_INTERVAL_TICKS - 1));
        assertTrue(ContainerInventoryMath.shouldRestartMining(false,
                ContainerInventoryMath.RESTART_INTERVAL_TICKS));
    }
}
