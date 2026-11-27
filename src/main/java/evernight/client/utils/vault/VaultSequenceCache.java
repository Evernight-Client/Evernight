package evernight.client.utils.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-(seed, ominous) Xoroshiro checkpoints every {@link #INTERVAL} openings.
 *
 * <p>The global vault sequence has data-dependent draw counts ({@code enchant_with_levels}), so it
 * cannot be jumped with a single matrix power. Instead the cache derives checkpoint states
 * sequentially once and every later scan restarts a chunk from the nearest checkpoint. That keeps
 * random offsets cheap after the first walk and, importantly, makes the state independent of the
 * 32-bit offset arithmetic the old native engine used.
 *
 * <p>Only 16 bytes per 65,536 openings are retained; a 50 million offset costs about 12 KiB.
 */
final class VaultSequenceCache {
    static final int INTERVAL = 1 << 16;

    private static final ConcurrentHashMap<Key, Cache> CACHES = new ConcurrentHashMap<>();

    private VaultSequenceCache() {
    }

    record State(long lo, long hi) {
    }

    private record Key(long seed, boolean ominous) {
    }

    static State stateAt(long seed, boolean ominous, long offset) {
        long checkpoint = offset / INTERVAL;
        Cache cache = CACHES.computeIfAbsent(new Key(seed, ominous), key -> new Cache(seed, ominous));
        return cache.state(checkpoint);
    }

    static void clear() {
        CACHES.clear();
    }

    private static final class Cache {
        private final long seed;
        private final boolean ominous;
        private final List<State> states = new ArrayList<>();
        private final VaultLootGenerator generator = new VaultLootGenerator();

        Cache(long seed, boolean ominous) {
            this.seed = seed;
            this.ominous = ominous;
        }

        synchronized State state(long checkpoint) {
            if (checkpoint < 0) {
                checkpoint = 0;
            }
            if (this.states.isEmpty()) {
                this.generator.reset(this.seed, this.ominous);
                this.states.add(new State(this.generator.stateLo(), this.generator.stateHi()));
            }
            while (this.states.size() <= checkpoint) {
                State previous = this.states.get(this.states.size() - 1);
                this.generator.reset(previous.lo(), previous.hi());
                for (int i = 0; i < INTERVAL; i++) {
                    this.generator.advance(this.ominous ? VaultLootData.OMINOUS : VaultLootData.NORMAL);
                }
                this.states.add(new State(this.generator.stateLo(), this.generator.stateHi()));
            }
            return this.states.get((int) checkpoint);
        }
    }
}
