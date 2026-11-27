package evernight.client.utils.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionUtilsTest {
    @Test
    @DisplayName("空/短/大/随机载荷在各压缩级别下 roundtrip")
    void roundTripAtSupportedLevels() {
        byte[] empty = new byte[0];
        byte[] shortPayload = "hello 世界".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] large = new byte[64 * 1024];
        new Random(42L).nextBytes(large);
        byte[] repetitive = new byte[64 * 1024];
        java.util.Arrays.fill(repetitive, (byte) 'A');

        for (int level : new int[] {0, 1, 6, 9}) {
            assertRoundTrip(empty, level);
            assertRoundTrip(shortPayload, level);
            assertRoundTrip(large, level);
            assertRoundTrip(repetitive, level);
        }
    }

    @Test
    @DisplayName("重复数据压缩后明显变小")
    void repetitiveDataCompressesSmaller() {
        byte[] repetitive = new byte[64 * 1024];
        java.util.Arrays.fill(repetitive, (byte) 'A');
        byte[] compressed = CompressionUtils.deflate(repetitive, 9);
        assertTrue(compressed.length < repetitive.length);
    }

    @Test
    @DisplayName("坏压缩数据抛 IllegalArgumentException")
    void invalidDataIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CompressionUtils.inflate(new byte[] {1, 2, 3, 4, 5}));
    }

    private static void assertRoundTrip(byte[] payload, int level) {
        byte[] compressed = CompressionUtils.deflate(payload, level);
        assertArrayEquals(payload, CompressionUtils.inflate(compressed),
                "roundtrip level=" + level + " length=" + payload.length);
    }
}
