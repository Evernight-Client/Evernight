package evernight.client.utils.vault;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helpers for the ominous vault loot prediction, kept free of the Minecraft runtime so the
 * scaling rules stay readable and testable.
 *
 * <p>Wording and labels live in the language table; this class only deals with numbers and order.
 */
public final class VaultLootLogic {
    private VaultLootLogic() {
    }

    /** One target item drop reported by the engine: the N-th ominous vault opened worldwide. */
    public record Hit(long offset, String itemId, int count) {
    }

    /** A hit resolved for reporting, with the same offset semantics as {@link Hit}. */
    public record Occurrence(long offset, String itemId, int count) {
    }

    /**
     * Converts minutes since the previous capture into how many vaults others may have opened since.
     *
     * <p>Rule of thumb: 10 s / 30 opens, 20 s / 60, 40 s / 120, 60 s / 200, then +200 per further
     * minute, capped at 1000.
     */
    public static int windowForSeconds(long seconds) {
        if (seconds <= 10) {
            return 30;
        }
        if (seconds <= 20) {
            return 60;
        }
        if (seconds <= 40) {
            return 120;
        }
        if (seconds <= 60) {
            return 200;
        }
        long window = ((seconds + 59) / 60) * 200;
        return (int) Math.min(1000, window);
    }

    /** First {@code limit} occurrences, preserving order. */
    public static List<Occurrence> firstN(List<Occurrence> occurrences, int limit) {
        if (occurrences.size() <= limit) {
            return occurrences;
        }
        return new ArrayList<>(occurrences.subList(0, limit));
    }

    /** Engine hits in report order. */
    public static List<Occurrence> occurrences(List<Hit> hits) {
        List<Occurrence> occurrences = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            occurrences.add(new Occurrence(hit.offset(), hit.itemId(), hit.count()));
        }
        return occurrences;
    }
}
