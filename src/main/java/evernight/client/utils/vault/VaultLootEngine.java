package evernight.client.utils.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Facade over the pure-Java prediction core.
 *
 * <p>The former implementation started a per-platform {@code vaulttool} binary and parsed its
 * stdout. The core now lives in {@link VaultLootCore}: same global vanilla sequence, same offset
 * semantics, but no native binary, no subprocess, no extracted tools directory, and no platform
 * hiding. Offsets are long-based, so the old 50-million ceiling is gone as well.
 *
 * <p>The 1.21.11 and 26.1.2 trial-chamber tables and random-sequence semantics are identical, so the
 * facade no longer takes a game-version token.
 */
public final class VaultLootEngine {
    private VaultLootEngine() {
    }

    /**
     * Scans {@code limit} openings starting after {@code offset} and returns at most {@code amount}
     * hits for the semicolon-separated item ids in {@code targetSpec}.
     */
    public static List<VaultLootLogic.Hit> search(long seed, boolean ominous, long offset, long limit,
                                                  int amount, String targetSpec,
                                                  LongConsumer progress) {
        List<VaultLootCore.Hit> hits = VaultLootCore.search(seed, ominous, offset, limit, amount,
                targetSpec, progress);
        List<VaultLootLogic.Hit> result = new ArrayList<>(hits.size());
        for (VaultLootCore.Hit hit : hits) {
            result.add(new VaultLootLogic.Hit(hit.offset(), hit.itemId(), hit.count()));
        }
        return result;
    }

    /** Every 1-based offset in {@code [1, maxScan]} whose full drop sequence matches {@code spec}. */
    public static List<Long> matches(long seed, boolean ominous, long maxScan, String spec,
                                     LongConsumer progress) {
        return VaultLootCore.matches(seed, ominous, maxScan, spec, progress);
    }

    /**
     * Keeps the candidates reachable from a previous candidate within {@code delta} openings.
     * {@code rangeStart} and {@code rangeEnd} are 0-based indices; results are 1-based offsets.
     */
    public static List<Long> narrow(long seed, boolean ominous, long rangeStart, long rangeEnd,
                                    long delta, String spec, List<Long> candidates,
                                    LongConsumer progress) {
        return VaultLootCore.narrow(seed, ominous, rangeStart, rangeEnd, delta, spec, candidates,
                progress);
    }

    /** First matching offset in {@code [start, start + maxAttempts)}, or {@code -1}. */
    public static long detect(long seed, boolean ominous, long start, long maxAttempts, String spec,
                              LongConsumer progress) {
        return VaultLootCore.detect(seed, ominous, start, maxAttempts, spec, progress);
    }

    /** Clears checkpoint state, e.g. when the module is disabled or the seed changes. */
    public static void clearCache() {
        VaultSequenceCache.clear();
    }
}
