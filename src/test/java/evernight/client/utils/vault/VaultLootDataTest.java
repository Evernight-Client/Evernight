package evernight.client.utils.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural invariants of the generated vault data tables. These catch a bad regeneration (wrong
 * array length, an out-of-range index or a weight sum that no longer matches) before the generator
 * reads past an array.
 */
class VaultLootDataTest {
    @Test
    @DisplayName("物品/附魔/附魔随机列表的数组长度一致")
    void arrayLengthsMatchConstants() {
        assertEquals(VaultLootData.ITEM_COUNT, VaultLootData.ITEM_NAMES.length);
        assertEquals(VaultLootData.ITEM_COUNT, VaultLootData.ITEM_ENCHANTABILITY.length);
        assertEquals(VaultLootData.ITEM_COUNT, VaultLootData.ITEM_PRIMARY_ENCHANTMENTS.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_NAMES.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_WEIGHT.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_MAX_LEVEL.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_MIN_BASE.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_MIN_PER_LEVEL.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_MAX_BASE.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_MAX_PER_LEVEL.length);
        assertEquals(VaultLootData.ENCHANTMENT_COUNT, VaultLootData.ENCHANTMENT_INCOMPATIBLE.length);
        assertEquals(VaultLootData.FUNCTION_TYPE.length, VaultLootData.FUNCTION_A.length);
        assertEquals(VaultLootData.FUNCTION_TYPE.length, VaultLootData.FUNCTION_B.length);
        assertEquals(VaultLootData.FUNCTION_TYPE.length, VaultLootData.FUNCTION_C.length);
        assertEquals(VaultLootData.ENTRY_ITEM.length, VaultLootData.ENTRY_WEIGHT.length);
        assertEquals(VaultLootData.ENTRY_ITEM.length, VaultLootData.ENTRY_FUNCTION_START.length);
        assertEquals(VaultLootData.ENTRY_ITEM.length, VaultLootData.ENTRY_FUNCTION_COUNT.length);
    }

    @Test
    @DisplayName("物品主附魔掩码不越界")
    void primaryEnchantmentMasksStayInRange() {
        for (int item = 0; item < VaultLootData.ITEM_COUNT; item++) {
            long mask = VaultLootData.ITEM_PRIMARY_ENCHANTMENTS[item];
            assertTrue(mask >= 0L, "item " + item + " mask is negative");
            assertEquals(0L, mask >>> VaultLootData.ENCHANTMENT_COUNT,
                    "item " + item + " mask has a bit beyond the enchantment table");
        }
    }

    @Test
    @DisplayName("on_random_loot 无重复且 id 合法")
    void onRandomLootIsValid() {
        Set<Integer> seen = new HashSet<>();
        for (int enchantment : VaultLootData.ON_RANDOM_LOOT) {
            assertTrue(enchantment >= 0 && enchantment < VaultLootData.ENCHANTMENT_COUNT,
                    "enchantment=" + enchantment);
            assertTrue(seen.add(enchantment), "duplicate enchantment=" + enchantment);
        }
        assertFalse(seen.isEmpty());
    }

    @Test
    @DisplayName("随机附魔列表的 id/等级合法")
    void randomEnchantmentListsAreValid() {
        for (int[] list : VaultLootData.ENCHANT_RANDOM_LISTS) {
            assertTrue(list.length > 0);
            for (int enchantment : list) {
                assertTrue(enchantment >= 0 && enchantment < VaultLootData.ENCHANTMENT_COUNT);
                assertTrue(VaultLootData.ENCHANTMENT_MAX_LEVEL[enchantment] >= 1);
            }
        }
    }

    @Test
    @DisplayName("每个池的权重和与总权重一致，条目索引合法")
    void poolWeightsAndEntriesAreConsistent() {
        assertEquals(VaultLootData.TABLE_POOL_START.length, VaultLootData.TABLE_POOL_COUNT.length);
        assertEquals(VaultLootData.POOL_ENTRY_START.length, VaultLootData.POOL_ENTRY_COUNT.length);
        assertEquals(VaultLootData.POOL_ENTRY_START.length, VaultLootData.POOL_TOTAL_WEIGHT.length);
        for (int pool = 0; pool < VaultLootData.POOL_ENTRY_START.length; pool++) {
            int entryStart = VaultLootData.POOL_ENTRY_START[pool];
            int entryCount = VaultLootData.POOL_ENTRY_COUNT[pool];
            assertTrue(entryStart >= 0);
            assertTrue(entryCount >= 0);
            assertTrue(entryStart + entryCount <= VaultLootData.ENTRY_ITEM.length,
                    "pool " + pool + " entry range out of bounds");
            assertTrue(VaultLootData.POOL_MIN_ROLLS[pool] >= 0);
            assertTrue(VaultLootData.POOL_MAX_ROLLS[pool] >= VaultLootData.POOL_MIN_ROLLS[pool]);
            assertTrue(VaultLootData.POOL_CHANCE[pool] < 0.0F
                            || (VaultLootData.POOL_CHANCE[pool] >= 0.0F
                            && VaultLootData.POOL_CHANCE[pool] <= 1.0F),
                    "pool " + pool + " chance=" + VaultLootData.POOL_CHANCE[pool]);

            int weightSum = 0;
            for (int entry = entryStart; entry < entryStart + entryCount; entry++) {
                assertTrue(VaultLootData.ENTRY_WEIGHT[entry] > 0, "entry " + entry + " weight");
                weightSum += VaultLootData.ENTRY_WEIGHT[entry];
                int item = VaultLootData.ENTRY_ITEM[entry];
                assertTrue(item == -1 || (item >= 0 && item < VaultLootData.ITEM_COUNT),
                        "entry " + entry + " item=" + item);
                int functionStart = VaultLootData.ENTRY_FUNCTION_START[entry];
                int functionCount = VaultLootData.ENTRY_FUNCTION_COUNT[entry];
                assertTrue(functionStart >= 0 && functionCount >= 0);
                assertTrue(functionStart + functionCount <= VaultLootData.FUNCTION_TYPE.length,
                        "entry " + entry + " function range out of bounds");
            }
            assertEquals(weightSum, VaultLootData.POOL_TOTAL_WEIGHT[pool],
                    "pool " + pool + " total weight");
        }
    }

    @Test
    @DisplayName("每个 loot table 的池范围合法")
    void tablePoolRangesAreValid() {
        for (int table = 0; table < VaultLootData.TABLE_POOL_START.length; table++) {
            int start = VaultLootData.TABLE_POOL_START[table];
            int count = VaultLootData.TABLE_POOL_COUNT[table];
            assertTrue(start >= 0 && count > 0, "table " + table);
            assertTrue(start + count <= VaultLootData.POOL_ENTRY_START.length,
                    "table " + table + " pool range out of bounds");
        }
    }

    @Test
    @DisplayName("函数操作码与参数范围合法")
    void functionOpcodesAreValid() {
        for (int function = 0; function < VaultLootData.FUNCTION_TYPE.length; function++) {
            int type = VaultLootData.FUNCTION_TYPE[function];
            int a = VaultLootData.FUNCTION_A[function];
            int b = VaultLootData.FUNCTION_B[function];
            int c = VaultLootData.FUNCTION_C[function];
            switch (type) {
                case VaultLootData.FN_COUNT_UNIFORM -> {
                    assertTrue(a >= 0 && a <= b, "function " + function);
                }
                case VaultLootData.FN_COUNT_CONSTANT -> assertTrue(a >= 0);
                case VaultLootData.FN_DAMAGE -> {
                    assertTrue(a >= 0 && a <= b && b <= 10000, "function " + function);
                }
                case VaultLootData.FN_POTION -> assertTrue(
                        a >= 0 && a < VaultLootData.POTION_NAMES.length, "function " + function);
                case VaultLootData.FN_OMINOUS_AMPLIFIER -> assertTrue(
                        a >= 0 && a <= b && b <= 4, "function " + function);
                case VaultLootData.FN_ENCHANT_WITH_LEVELS -> assertTrue(
                        b >= 0 && b <= c && c <= 64, "function " + function);
                case VaultLootData.FN_ENCHANT_RANDOMLY -> assertTrue(
                        a >= 0 && a < VaultLootData.ENCHANT_RANDOM_LISTS.length, "function " + function);
                case VaultLootData.FN_SET_ENCHANTMENT -> assertTrue(
                        a >= 0 && a < VaultLootData.ENCHANTMENT_COUNT && b >= 1
                                && b <= VaultLootData.ENCHANTMENT_MAX_LEVEL[a], "function " + function);
                case VaultLootData.FN_NESTED_TABLE -> assertTrue(
                        a >= 0 && a < VaultLootData.TABLE_POOL_START.length, "function " + function);
                default -> throw new AssertionError("unknown function type " + type);
            }
        }
    }

    @Test
    @DisplayName("附魔互斥掩码没有越界位")
    void incompatibilityMasksStayInRange() {
        for (int enchantment = 0; enchantment < VaultLootData.ENCHANTMENT_COUNT; enchantment++) {
            long mask = VaultLootData.ENCHANTMENT_INCOMPATIBLE[enchantment];
            assertTrue(mask >= 0L);
            assertEquals(0L, mask >>> VaultLootData.ENCHANTMENT_COUNT,
                    "enchantment " + enchantment + " mask has a bit beyond the table");
        }
    }
}
