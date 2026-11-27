package evernight.client.utils.vault;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies that the checkpoint cache reconstructs the same Xoroshiro state as walking the global
 * vault sequence continuously.
 */
class VaultSequenceCacheTest {
    private static final long SEED = 0x0FED_CBA9_8765_4321L;
    private static final long CHECKPOINT = VaultSequenceCache.INTERVAL;

    @BeforeEach
    @AfterEach
    void clearCache() {
        VaultSequenceCache.clear();
    }

    @Test
    @DisplayName("checkpoint 0 与刚 seed 的生成器状态一致")
    void checkpointZeroMatchesFreshSeed() {
        VaultSequenceCache.State cached = VaultSequenceCache.stateAt(SEED, false, 0L);
        VaultLootGenerator fresh = new VaultLootGenerator();
        fresh.reset(SEED, false);
        assertEquals(fresh.stateLo(), cached.lo());
        assertEquals(fresh.stateHi(), cached.hi());
    }

    @Test
    @DisplayName("checkpoint 1 与连续 advance 65,536 次后的状态一致")
    void checkpointOneMatchesContinuousAdvance() {
        VaultSequenceCache.State cached = VaultSequenceCache.stateAt(SEED, true, CHECKPOINT);
        VaultLootGenerator continuous = new VaultLootGenerator();
        continuous.reset(SEED, true);
        for (int index = 0; index < CHECKPOINT; index++) {
            continuous.advance(VaultLootData.OMINOUS);
        }
        assertEquals(continuous.stateLo(), cached.lo());
        assertEquals(continuous.stateHi(), cached.hi());
    }

    @Test
    @DisplayName("负 checkpoint 归零，重复读取/clear 后重建结果不变")
    void negativeCheckpointClampsAndClearRebuilds() {
        VaultSequenceCache.State zero = VaultSequenceCache.stateAt(SEED, false, 0L);
        VaultSequenceCache.State negative = VaultSequenceCache.stateAt(SEED, false, -CHECKPOINT);
        VaultSequenceCache.State repeated = VaultSequenceCache.stateAt(SEED, false, 0L);
        assertEquals(zero, negative);
        assertEquals(zero, repeated);

        VaultSequenceCache.clear();
        assertEquals(zero, VaultSequenceCache.stateAt(SEED, false, 0L));
    }

    @Test
    @DisplayName("缓存按 seed 与 ominous 区分")
    void cacheIsKeyedBySeedAndTable() {
        VaultSequenceCache.State normal = VaultSequenceCache.stateAt(SEED, false, 0L);
        VaultSequenceCache.State ominous = VaultSequenceCache.stateAt(SEED, true, 0L);
        VaultSequenceCache.State otherSeed = VaultSequenceCache.stateAt(SEED + 1L, false, 0L);
        assertNotEquals(normal, ominous);
        assertNotEquals(normal, otherSeed);
    }
}
