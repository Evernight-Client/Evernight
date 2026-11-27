package evernight.client.utils.vault;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the vault predictor's Xoroshiro128++ implementation is the same sequence vanilla
 * builds for the two vault loot tables.
 *
 * <p>The reference is Minecraft's own {@link RandomSequence}/{@link RandomSource} on the test
 * classpath, so the expected values are not hand-derived. No game bootstrap is needed for these
 * classes.
 */
class VaultRandomTest {
    private static final String NORMAL_TABLE = "minecraft:chests/trial_chambers/reward";
    private static final String OMINOUS_TABLE = "minecraft:chests/trial_chambers/reward_ominous";
    private static final long[] SEEDS = {
            0L,
            1L,
            -1L,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            -7346913998703726680L
    };
    private static final int DRAW_COUNT = 256;

    @Test
    @DisplayName("nextLong 与 vanilla RandomSequence 完全一致（两张表、多个 seed）")
    void nextLongMatchesVanilla() {
        for (long seed : SEEDS) {
            assertNextLongsMatch(seed, false);
            assertNextLongsMatch(seed, true);
        }
    }

    @Test
    @DisplayName("nextInt() 与 vanilla 完全一致")
    void nextIntMatchesVanilla() {
        for (long seed : SEEDS) {
            for (boolean ominous : new boolean[] {false, true}) {
                VaultRandom predicted = seeded(seed, ominous);
                RandomSource vanilla = vanilla(seed, ominous);
                for (int index = 0; index < DRAW_COUNT; index++) {
                    assertEquals(vanilla.nextInt(), predicted.nextInt(),
                            "nextInt seed=" + seed + " ominous=" + ominous + " draw=" + index);
                }
            }
        }
    }

    @Test
    @DisplayName("nextInt(bound) 与 vanilla 的乘高位/拒绝采样一致")
    void nextIntWithBoundMatchesVanilla() {
        int[] bounds = {1, 2, 3, 7, 16, 100, 1000};
        for (long seed : SEEDS) {
            for (boolean ominous : new boolean[] {false, true}) {
                for (int bound : bounds) {
                    VaultRandom predicted = seeded(seed, ominous);
                    RandomSource vanilla = vanilla(seed, ominous);
                    for (int index = 0; index < 512; index++) {
                        assertEquals(vanilla.nextInt(bound), predicted.nextInt(bound),
                                "nextInt(" + bound + ") seed=" + seed + " ominous=" + ominous
                                        + " draw=" + index);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("nextFloat 的高 24 位与 vanilla 完全一致")
    void nextFloatMatchesVanilla() {
        for (long seed : SEEDS) {
            for (boolean ominous : new boolean[] {false, true}) {
                VaultRandom predicted = seeded(seed, ominous);
                RandomSource vanilla = vanilla(seed, ominous);
                for (int index = 0; index < DRAW_COUNT; index++) {
                    float expected = vanilla.nextFloat();
                    float actual = predicted.nextFloat();
                    assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual),
                            "nextFloat seed=" + seed + " ominous=" + ominous + " draw=" + index);
                }
            }
        }
    }

    @Test
    @DisplayName("混合 draw 顺序保持一致，说明每次抽取消耗的位完全相同")
    void mixedDrawOrderMatchesVanilla() {
        for (long seed : SEEDS) {
            for (boolean ominous : new boolean[] {false, true}) {
                VaultRandom predicted = seeded(seed, ominous);
                RandomSource vanilla = vanilla(seed, ominous);
                for (int index = 0; index < DRAW_COUNT; index++) {
                    switch (index % 4) {
                        case 0 -> assertEquals(vanilla.nextLong(), predicted.nextLong(),
                                "mixed nextLong seed=" + seed + " ominous=" + ominous + " draw=" + index);
                        case 1 -> assertEquals(vanilla.nextInt(), predicted.nextInt(),
                                "mixed nextInt seed=" + seed + " ominous=" + ominous + " draw=" + index);
                        case 2 -> assertEquals(vanilla.nextInt(97), predicted.nextInt(97),
                                "mixed nextInt(97) seed=" + seed + " ominous=" + ominous + " draw=" + index);
                        default -> assertEquals(
                                Float.floatToRawIntBits(vanilla.nextFloat()),
                                Float.floatToRawIntBits(predicted.nextFloat()),
                                "mixed nextFloat seed=" + seed + " ominous=" + ominous + " draw=" + index);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("state/lo/hi 可把检查点状态原样传给另一个实例")
    void checkpointStateCanBeRestored() {
        long seed = 0x5DEECE66DL;
        VaultRandom source = seeded(seed, true);
        // Advance away from the initial state so the copied state is not just the seed.
        for (int index = 0; index < 37; index++) {
            source.nextLong();
        }
        VaultRandom restored = new VaultRandom();
        restored.state(source.lo(), source.hi());
        for (int index = 0; index < 128; index++) {
            assertEquals(source.nextLong(), restored.nextLong(),
                    "restored checkpoint draw=" + index);
        }
    }

    @Test
    @DisplayName("非正 bound 抛 IllegalArgumentException")
    void nonPositiveBoundsAreRejected() {
        VaultRandom random = seeded(1L, false);
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(-1));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(Integer.MIN_VALUE));
    }

    @Test
    @DisplayName("trace 记录每次带 bound 的 nextInt 与实际结果")
    void traceRecordsBoundedDraws() {
        VaultRandom random = seeded(42L, false);
        random.startTrace();
        int bounded = random.nextInt(17);
        float floatDraw = random.nextFloat();
        String trace = random.trace();
        assertTrue(trace.contains("nextInt(17)=" + bounded), trace);
        assertTrue(trace.contains("nextFloat()=" + floatDraw), trace);
    }

    @Test
    @DisplayName("同 seed 可复现，不同表/不同 seed 产生不同序列")
    void seedingIsDeterministicAndTableSpecific() {
        VaultRandom first = seeded(7L, false);
        VaultRandom second = seeded(7L, false);
        VaultRandom ominous = seeded(7L, true);
        VaultRandom otherSeed = seeded(8L, false);
        boolean differsFromOminous = false;
        boolean differsFromOtherSeed = false;
        for (int index = 0; index < 64; index++) {
            long expected = first.nextLong();
            assertEquals(expected, second.nextLong(), "same seed must reproduce draw=" + index);
            differsFromOminous |= expected != ominous.nextLong();
            differsFromOtherSeed |= expected != otherSeed.nextLong();
        }
        assertNotEquals(first.lo(), ominous.lo());
        assertNotEquals(first.lo(), otherSeed.lo());
        assertTrue(differsFromOminous);
        assertTrue(differsFromOtherSeed);
    }

    private static VaultRandom seeded(long seed, boolean ominous) {
        VaultRandom random = new VaultRandom();
        random.seed(seed, ominous);
        return random;
    }

    private static void assertNextLongsMatch(long seed, boolean ominous) {
        VaultRandom predicted = seeded(seed, ominous);
        RandomSource vanilla = vanilla(seed, ominous);
        for (int index = 0; index < DRAW_COUNT; index++) {
            assertEquals(vanilla.nextLong(), predicted.nextLong(),
                    "nextLong seed=" + seed + " ominous=" + ominous + " draw=" + index);
        }
    }

    private static RandomSource vanilla(long seed, boolean ominous) {
        String table = ominous ? OMINOUS_TABLE : NORMAL_TABLE;
        return new RandomSequence(seed, Identifier.parse(table)).random();
    }
}
