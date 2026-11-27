package evernight.client.utils.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the load-bearing invariants of the vanilla {@code enchant_with_levels} reimplementation:
 * deterministic draws, bounded/unique selections and enchantment incompatibility.
 */
class VaultEnchantmentsTest {
    private static final long SEED = 0x1234_5678_90AB_CDEFL;

    @Test
    @DisplayName("同 seed/物品/等级的选择完全可复现")
    void selectionIsDeterministic() {
        VaultEnchantments.Scratch first = new VaultEnchantments.Scratch();
        VaultEnchantments.Scratch second = new VaultEnchantments.Scratch();
        int firstCount = select(SEED, VaultLootData.ITEM_BOOK, 30, first);
        int secondCount = select(SEED, VaultLootData.ITEM_BOOK, 30, second);
        assertEquals(firstCount, secondCount);
        assertArrayEquals(Arrays.copyOf(first.selectedEnchantment, firstCount),
                Arrays.copyOf(second.selectedEnchantment, secondCount));
        assertArrayEquals(Arrays.copyOf(first.selectedLevel, firstCount),
                Arrays.copyOf(second.selectedLevel, secondCount));
    }

    @Test
    @DisplayName("附魔书的选择满足数量/去重/等级/候选/互斥约束")
    void bookSelectionRespectsVanillaConstraints() {
        for (long seed = 0L; seed < 8L; seed++) {
            VaultEnchantments.Scratch scratch = new VaultEnchantments.Scratch();
            int count = select(seed, VaultLootData.ITEM_BOOK, 30, scratch);
            assertTrue(count > 0, "enchanted book at level 30 should select something, seed=" + seed);
            assertConstraints(VaultLootData.ITEM_BOOK, count, scratch);
        }
    }

    @Test
    @DisplayName("有主附魔的装备只从 applicable 集合选择")
    void equipmentSelectionUsesPrimaryEnchantments() {
        for (int item : new int[] {VaultLootData.ITEM_SHIELD, 8, 22, 29}) {
            VaultEnchantments.Scratch scratch = new VaultEnchantments.Scratch();
            int count = select(SEED, item, 30, scratch);
            assertConstraints(item, count, scratch);
        }
    }

    @Test
    @DisplayName("没有 applicable 附魔的物品返回 0，不越界")
    void itemWithoutCandidatesReturnsZero() {
        VaultEnchantments.Scratch scratch = new VaultEnchantments.Scratch();
        assertEquals(0, select(SEED, 0, 30, scratch));
        assertEquals(0, select(SEED, 5, 30, new VaultEnchantments.Scratch()));
    }

    @Test
    @DisplayName("所有物品/等级组合都不抛异常且不越界")
    void everyItemLevelCombinationIsSafe() {
        for (int item = 0; item < VaultLootData.ITEM_COUNT; item++) {
            for (int level = 1; level <= 64; level++) {
                VaultEnchantments.Scratch scratch = new VaultEnchantments.Scratch();
                int count = select(item * 1000L + level, item, level, scratch);
                assertTrue(count >= 0 && count <= 32, "count=" + count);
            }
        }
    }

    private static int select(long seed, int item, int level, VaultEnchantments.Scratch scratch) {
        VaultRandom random = new VaultRandom();
        random.seed(seed, true);
        return VaultEnchantments.select(random, item, level, scratch);
    }

    private static void assertConstraints(int item, int count, VaultEnchantments.Scratch scratch) {
        assertTrue(count >= 0 && count <= 32, "count=" + count);
        boolean[] seen = new boolean[VaultLootData.ENCHANTMENT_COUNT];
        for (int index = 0; index < count; index++) {
            int enchantment = scratch.selectedEnchantment[index];
            assertTrue(enchantment >= 0 && enchantment < VaultLootData.ENCHANTMENT_COUNT,
                    "enchantment=" + enchantment);
            assertFalse(seen[enchantment], "duplicate enchantment=" + enchantment);
            seen[enchantment] = true;
            int level = scratch.selectedLevel[index];
            assertTrue(level >= 1 && level <= VaultLootData.ENCHANTMENT_MAX_LEVEL[enchantment],
                    "level=" + level + " enchantment=" + enchantment);
            assertTrue(isApplicable(item, enchantment),
                    "enchantment " + enchantment + " is not applicable to item " + item);
            assertTrue(containsOnRandom(enchantment));
        }
        for (int left = 0; left < count; left++) {
            for (int right = left + 1; right < count; right++) {
                int leftId = scratch.selectedEnchantment[left];
                int rightId = scratch.selectedEnchantment[right];
                assertFalse((VaultLootData.ENCHANTMENT_INCOMPATIBLE[leftId] & (1L << rightId)) != 0,
                        "incompatible pair: " + leftId + " / " + rightId);
                assertFalse((VaultLootData.ENCHANTMENT_INCOMPATIBLE[rightId] & (1L << leftId)) != 0,
                        "incompatible pair: " + rightId + " / " + leftId);
            }
        }
    }

    private static boolean isApplicable(int item, int enchantment) {
        if (item == VaultLootData.ITEM_BOOK) {
            return containsOnRandom(enchantment);
        }
        return (VaultLootData.ITEM_PRIMARY_ENCHANTMENTS[item] & (1L << enchantment)) != 0;
    }

    private static boolean containsOnRandom(int enchantment) {
        for (int candidate : VaultLootData.ON_RANDOM_LOOT) {
            if (candidate == enchantment) {
                return true;
            }
        }
        return false;
    }
}
