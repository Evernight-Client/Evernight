package evernight.client.utils.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the capture/detection contract of {@link VaultLootSpec}: parsing a semicolon separated item
 * sequence and matching it against a generated vault opening in either ejection order.
 */
class VaultLootSpecTest {
    private static final long SEED = 0L;
    /** Indices into {@link VaultLootData#ITEM_NAMES}; the table intentionally has no per-item constants. */
    private static final int DIAMOND_ITEM = 5;
    private static final int EMERALD_ITEM = 9;

    @Test
    @DisplayName("由真实开箱结果构造的 spec 能匹配正序与反序")
    void parsedSpecMatchesActualOpeningInBothOrders() {
        VaultLootGenerator opening = generateOpening(SEED, true);
        assertTrue(opening.size() > 0, "fixture opening must contain drops");

        VaultLootSpec forward = VaultLootSpec.parse(specFor(opening, false));
        VaultLootSpec reversed = VaultLootSpec.parse(specFor(opening, true));

        assertEquals(opening.size(), forward.size());
        assertTrue(forward.matches(opening));
        assertTrue(reversed.matches(opening));
    }

    @Test
    @DisplayName("item 或 count 任一不同都不匹配")
    void differentItemOrCountDoesNotMatch() {
        VaultLootGenerator opening = generateOpening(SEED, true);
        assertTrue(opening.size() > 0);

        int firstItem = opening.item(0);
        int differentItem = firstItem == DIAMOND_ITEM ? EMERALD_ITEM : DIAMOND_ITEM;
        String differentItemSpec = replaceFirstEntry(opening,
                VaultLootData.ITEM_NAMES[differentItem], opening.count(0), null);
        assertFalse(VaultLootSpec.parse(differentItemSpec).matches(opening));

        String differentCountSpec = replaceFirstEntry(opening,
                VaultLootData.ITEM_NAMES[firstItem], opening.count(0) + 1, null);
        assertFalse(VaultLootSpec.parse(differentCountSpec).matches(opening));
    }

    @Test
    @DisplayName("不在宝库表中的物品永远不匹配")
    void unknownItemNeverMatches() {
        VaultLootGenerator opening = generateOpening(SEED, true);
        assertTrue(opening.size() > 0);
        String spec = replaceFirstEntry(opening, "minecraft:not_a_vault_item", opening.count(0), null);
        assertFalse(VaultLootSpec.parse(spec).matches(opening));
    }

    @Test
    @DisplayName("item id 可以省略 minecraft: 命名空间")
    void missingNamespaceIsAccepted() {
        VaultLootGenerator opening = generateOpening(SEED, true);
        assertTrue(opening.size() > 0);
        String fullName = VaultLootData.ITEM_NAMES[opening.item(0)];
        String withoutNamespace = fullName.substring("minecraft:".length());
        String spec = replaceFirstEntry(opening, withoutNamespace, opening.count(0), null);
        assertTrue(VaultLootSpec.parse(spec).matches(opening));
    }

    @Test
    @DisplayName("组件后缀只对写了后缀的项生效，写错则不匹配")
    void componentSuffixIsCheckedOnlyWhenPresent() {
        VaultLootGenerator opening = findOpeningWithComponents();
        int componentIndex = -1;
        for (int index = 0; index < opening.size(); index++) {
            if (opening.components(index) != 0L) {
                componentIndex = index;
                break;
            }
        }
        assertTrue(componentIndex >= 0, "fixture must contain a component-bearing drop");
        long actualHash = opening.components(componentIndex);
        long wrongHash = actualHash == 1L ? 2L : 1L;
        String actualSpec = specFor(opening, false, componentIndex, actualHash);
        String wrongSpec = specFor(opening, false, componentIndex, wrongHash);
        String noComponentSpec = specFor(opening, false);

        VaultLootSpec actual = VaultLootSpec.parse(actualSpec);
        assertTrue(actual.matches(opening), "actual component hash must match");
        assertFalse(VaultLootSpec.parse(wrongSpec).matches(opening),
                "a different component hash must not match");
        assertTrue(VaultLootSpec.parse(noComponentSpec).matches(opening),
                "omitting the component suffix keeps the legacy item/count match");
    }

    @Test
    @DisplayName("非十六进制组件后缀按 0 处理，退回 item/count 匹配")
    void malformedComponentHashFallsBackToItemAndCount() {
        VaultLootGenerator opening = generateOpening(SEED, true);
        assertTrue(opening.size() > 0);
        String spec = replaceFirstEntry(opening, VaultLootData.ITEM_NAMES[opening.item(0)],
                opening.count(0), "not-hex");
        assertTrue(VaultLootSpec.parse(spec).matches(opening));
    }

    @Test
    @DisplayName("spec 长度必须与本次开箱的掉落数一致")
    void lengthMustMatchExactly() {
        VaultLootGenerator opening = generateOpening(SEED, true);
        assertTrue(opening.size() > 0);
        String base = specFor(opening, false);
        VaultLootSpec extra = VaultLootSpec.parse(base + ";minecraft:diamond:1");
        assertFalse(extra.matches(opening));

        if (opening.size() > 1) {
            String fewer = specFor(opening, false);
            fewer = fewer.substring(0, fewer.lastIndexOf(';'));
            assertFalse(VaultLootSpec.parse(fewer).matches(opening));
        }
    }

    @Test
    @DisplayName("空 spec、空段、无冒号段被忽略")
    void blankAndMalformedPartsAreIgnored() {
        assertEquals(0, VaultLootSpec.parse(null).size());
        assertEquals(0, VaultLootSpec.parse("").size());
        assertEquals(0, VaultLootSpec.parse(" ; ; ").size());
        assertEquals(0, VaultLootSpec.parse("diamond").size());
        assertEquals(0, VaultLootSpec.parse(":1").size());
        assertEquals(1, VaultLootSpec.parse("minecraft:diamond:2").size());
        assertEquals(2, VaultLootSpec.parse("minecraft:diamond:2;;minecraft:emerald:1").size());
    }

    @Test
    @DisplayName("坏 count 回落到 1")
    void malformedCountFallsBackToOne() {
        VaultLootGenerator opening = findOpeningWithFirstCountOne();
        StringBuilder spec = new StringBuilder(VaultLootData.ITEM_NAMES[opening.item(0)])
                .append(":not-a-number");
        for (int index = 1; index < opening.size(); index++) {
            spec.append(';').append(VaultLootData.ITEM_NAMES[opening.item(index)]).append(':')
                    .append(opening.count(index));
        }
        assertTrue(VaultLootSpec.parse(spec.toString()).matches(opening), spec.toString());
    }

    @Test
    @DisplayName("targetItems 支持 all、空白、命名空间省略和未知项过滤")
    void targetItemsParsesNames() {
        assertNull(VaultLootSpec.targetItems(null));
        assertNull(VaultLootSpec.targetItems(""));
        assertNull(VaultLootSpec.targetItems("   "));
        assertNull(VaultLootSpec.targetItems("all"));
        assertNull(VaultLootSpec.targetItems("minecraft:not_a_vault_item"));
        assertArrayEquals(
                new int[] {DIAMOND_ITEM, EMERALD_ITEM},
                VaultLootSpec.targetItems("minecraft:diamond;emerald"));
        assertArrayEquals(
                new int[] {VaultLootData.ITEM_HEAVY_CORE},
                VaultLootSpec.targetItems("minecraft:not_a_vault_item;heavy_core"));
    }

    private static VaultLootGenerator generateOpening(long seed, boolean ominous) {
        VaultLootGenerator generator = new VaultLootGenerator();
        generator.reset(seed, ominous);
        generator.generate(ominous ? VaultLootData.OMINOUS : VaultLootData.NORMAL);
        return generator;
    }

    private static VaultLootGenerator findOpeningWithFirstCountOne() {
        for (long seed = 0; seed < 1000; seed++) {
            VaultLootGenerator opening = generateOpening(seed, true);
            if (opening.size() > 0 && opening.count(0) == 1) {
                return opening;
            }
        }
        throw new AssertionError("Could not find an opening whose first drop has count 1");
    }

    private static VaultLootGenerator findOpeningWithComponents() {
        for (long seed = 0; seed < 2000; seed++) {
            VaultLootGenerator opening = generateOpening(seed, true);
            for (int index = 0; index < opening.size(); index++) {
                if (opening.components(index) != 0L) {
                    return opening;
                }
            }
        }
        throw new AssertionError("Could not find a component-bearing vault drop");
    }

    private static String specFor(VaultLootGenerator opening, boolean reversed) {
        return specFor(opening, reversed, -1, 0L);
    }

    private static String specFor(VaultLootGenerator opening, boolean reversed, int hashIndex,
                                  long hash) {
        StringBuilder spec = new StringBuilder();
        for (int position = 0; position < opening.size(); position++) {
            int index = reversed ? opening.size() - 1 - position : position;
            if (spec.length() > 0) {
                spec.append(';');
            }
            spec.append(VaultLootData.ITEM_NAMES[opening.item(index)]).append(':')
                    .append(opening.count(index));
            if (index == hashIndex) {
                spec.append('@').append(Long.toUnsignedString(hash, 16));
            }
        }
        return spec.toString();
    }

    private static String replaceFirstEntry(VaultLootGenerator opening, String itemName, int count,
                                            String componentSuffix) {
        StringBuilder spec = new StringBuilder();
        spec.append(itemName).append(':').append(count);
        if (componentSuffix != null) {
            spec.append('@').append(componentSuffix);
        }
        for (int index = 1; index < opening.size(); index++) {
            spec.append(';').append(VaultLootData.ITEM_NAMES[opening.item(index)]).append(':')
                    .append(opening.count(index));
        }
        return spec.toString();
    }
}
