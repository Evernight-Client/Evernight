package evernight.client.utils.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the index-space contract of {@link VaultLootCore}: offsets are 1-based, scans are ordered,
 * and a scan that crosses the 65,536-opening checkpoint boundary sees the same sequence as a
 * continuous generator.
 */
class VaultLootCoreTest {
    private static final long SEED = 0x1234_5678_9ABC_DEF0L;
    private static final boolean OMINOUS = true;
    private static final long CHECKPOINT = VaultSequenceCache.INTERVAL;

    @Test
    @DisplayName("search 使用指定偏移、升序 1-based 结果并受 amount 限制")
    void searchRespectsOffsetLimitAndAmount() {
        List<VaultLootCore.Hit> hits = VaultLootCore.search(SEED, OMINOUS, 0L, 3L, 3, null);
        assertEquals(3, hits.size());
        assertSorted(hits);
        for (VaultLootCore.Hit hit : hits) {
            assertTrue(hit.offset() >= 1L && hit.offset() <= 3L, "offset=" + hit.offset());
            assertTrue(hit.count() > 0);
            assertTrue(hit.itemId().startsWith("minecraft:"));
        }

        List<VaultLootCore.Hit> empty = VaultLootCore.search(SEED, OMINOUS, 0L, 3L, 0, null);
        assertTrue(empty.isEmpty());
        assertTrue(VaultLootCore.search(SEED, OMINOUS, 0L, 0L, 5, null).isEmpty());
    }

    @Test
    @DisplayName("search 按目标物品过滤，并报告真实数量")
    void searchFiltersByTargetItem() {
        VaultLootGenerator opening = generateAt(0L);
        assertTrue(opening.size() > 0);
        String target = VaultLootData.ITEM_NAMES[opening.item(0)];
        List<VaultLootCore.Hit> expected = new ArrayList<>();
        for (int drop = 0; drop < opening.size(); drop++) {
            if (VaultLootData.ITEM_NAMES[opening.item(drop)].equals(target)) {
                expected.add(new VaultLootCore.Hit(1L, target, opening.count(drop)));
            }
        }

        List<VaultLootCore.Hit> actual = VaultLootCore.search(SEED, OMINOUS, 0L, 1L, 100, target);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("负 offset 按 0 处理")
    void searchClampsNegativeOffset() {
        List<VaultLootCore.Hit> fromZero = VaultLootCore.search(SEED, OMINOUS, 0L, 4L, 5, null);
        List<VaultLootCore.Hit> fromNegative = VaultLootCore.search(SEED, OMINOUS, -10L, 4L, 5, null);
        assertEquals(fromZero, fromNegative);
    }

    @Test
    @DisplayName("matches 返回 [1, maxScan] 内升序偏移")
    void matchesReturnsAscendingOffsets() {
        VaultLootGenerator opening = generateAt(4L);
        String spec = specOf(opening);
        List<Long> matches = VaultLootCore.matches(SEED, OMINOUS, 5L, spec);
        assertTrue(matches.contains(5L), "opening at index 4 is offset 5: " + matches);
        assertAscending(matches);

        List<Long> before = VaultLootCore.matches(SEED, OMINOUS, 4L, spec);
        assertFalse(before.contains(5L), "maxScan=4 must not include offset 5");
    }

    @Test
    @DisplayName("detect 返回首个匹配，未命中返回 -1")
    void detectReturnsFirstMatchOrMinusOne() {
        VaultLootGenerator opening = generateAt(5L);
        String spec = specOf(opening);
        assertEquals(6L, VaultLootCore.detect(SEED, OMINOUS, 5L, 1L, spec));

        String unknown = "minecraft:not_a_vault_item:1";
        assertEquals(-1L, VaultLootCore.detect(SEED, OMINOUS, 0L, 16L, unknown));
        assertEquals(-1L, VaultLootCore.detect(SEED, OMINOUS, 0L, 0L, spec));
    }

    @Test
    @DisplayName("narrow 只保留 delta 内且严格早于当前 offset 的候选")
    void narrowUsesPreviousCandidateWithinDelta() {
        VaultLootGenerator opening = generateAt(6L);
        String spec = specOf(opening);

        assertEquals(List.of(7L),
                VaultLootCore.narrow(SEED, OMINOUS, 6L, 7L, 2L, spec, List.of(5L)));
        assertTrue(VaultLootCore.narrow(SEED, OMINOUS, 6L, 7L, 2L, spec, List.of(7L)).isEmpty(),
                "the current offset is not a previous candidate");
        assertTrue(VaultLootCore.narrow(SEED, OMINOUS, 6L, 7L, 2L, spec, List.of(4L)).isEmpty(),
                "candidate outside delta must be dropped");
        assertTrue(VaultLootCore.narrow(SEED, OMINOUS, 6L, 6L, 2L, spec, List.of(5L)).isEmpty(),
                "empty range must return no matches");
    }

    @Test
    @DisplayName("第 65,536 次开箱的检查点状态与连续生成一致")
    void scanAcrossCheckpointMatchesContinuousSequence() {
        VaultLootGenerator expected = generateAt(CHECKPOINT + 1L);
        String spec = specOf(expected);
        assertEquals(CHECKPOINT + 2L,
                VaultLootCore.detect(SEED, OMINOUS, CHECKPOINT, 2L, spec));

        String target = VaultLootData.ITEM_NAMES[expected.item(0)];
        List<VaultLootCore.Hit> hits = VaultLootCore.search(SEED, OMINOUS, CHECKPOINT, 2L, 100,
                target);
        boolean found = false;
        for (VaultLootCore.Hit hit : hits) {
            if (hit.offset() == CHECKPOINT + 2L) {
                found = true;
                assertEquals(expected.count(0), hit.count());
            }
        }
        assertTrue(found, "checkpoint scan must include the continuous opening: " + hits);
        assertSorted(hits);
    }

    @Test
    @DisplayName("空范围/空 maxScan 返回空结果")
    void emptyRangesReturnEmpty() {
        assertTrue(VaultLootCore.matches(SEED, OMINOUS, 0L, "").isEmpty());
        assertTrue(VaultLootCore.matches(SEED, OMINOUS, -1L, "").isEmpty());
        assertTrue(VaultLootCore.narrow(SEED, OMINOUS, 5L, 5L, 1L, "minecraft:diamond:1",
                List.of(1L)).isEmpty());
    }

    private static VaultLootGenerator generateAt(long index) {
        VaultLootGenerator generator = new VaultLootGenerator();
        generator.reset(SEED, OMINOUS);
        for (long position = 0L; position <= index; position++) {
            generator.generate(VaultLootData.OMINOUS);
        }
        return generator;
    }

    private static String specOf(VaultLootGenerator opening) {
        StringBuilder spec = new StringBuilder();
        for (int index = 0; index < opening.size(); index++) {
            if (spec.length() > 0) {
                spec.append(';');
            }
            spec.append(VaultLootData.ITEM_NAMES[opening.item(index)]).append(':')
                    .append(opening.count(index));
        }
        return spec.toString();
    }

    private static void assertSorted(List<VaultLootCore.Hit> hits) {
        long previous = 0L;
        for (VaultLootCore.Hit hit : hits) {
            assertTrue(hit.offset() >= previous, "hits must be sorted by offset: " + hits);
            previous = hit.offset();
        }
    }

    private static void assertAscending(List<Long> offsets) {
        long previous = 0L;
        for (long offset : offsets) {
            assertTrue(offset > previous, "offsets must be strictly ascending: " + offsets);
            previous = offset;
        }
    }
}
