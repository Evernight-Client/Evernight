package evernight.client.utils.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateCensorTest {
    @Test
    @DisplayName("五位数以上/负数/小数被识别，四位/单词内数字/版本号不误报")
    void coordinateDetection() {
        assertTrue(CoordinateCensor.containsCoordinate("12345"));
        assertTrue(CoordinateCensor.containsCoordinate("x=-12345"));
        assertTrue(CoordinateCensor.containsCoordinate("12345.678"));
        assertTrue(CoordinateCensor.containsCoordinate("seed 123456789"));
        assertTrue(CoordinateCensor.containsCoordinate("§a 12345"));

        assertFalse(CoordinateCensor.containsCoordinate("1234"));
        assertFalse(CoordinateCensor.containsCoordinate("abc12345"));
        assertFalse(CoordinateCensor.containsCoordinate("version 1.21.11"));
        assertFalse(CoordinateCensor.containsCoordinate(null));
    }

    @Test
    @DisplayName("Base64 需要长度/大小写/数字或符号同时满足")
    void base64Detection() {
        assertTrue(CoordinateCensor.containsBase64("AbCdEfGh12345678"));
        assertTrue(CoordinateCensor.containsBase64("token AbCdEfGhIjKlMnOp1234;"));
        assertFalse(CoordinateCensor.containsBase64("abcdefgh12345678"));
        assertFalse(CoordinateCensor.containsBase64("AbCdEfGhIjKlMnOp"));
        assertFalse(CoordinateCensor.containsBase64("AbCdEfGh1234567"));
        assertFalse(CoordinateCensor.containsBase64("AbCdEfGh123456789"));
        assertFalse(CoordinateCensor.containsBase64("https://example.com/short"));
        assertFalse(CoordinateCensor.containsBase64(null));
        assertFalse(CoordinateCensor.containsBase64(""));
    }

    @Test
    @DisplayName("censorAllNumbers 替换所有数字但保留 § 格式码")
    void censorAllNumbersKeepsFormatting() {
        assertEquals("§aHello xxx xxx", CoordinateCensor.censorAllNumbers("§aHello 123 456"));
        assertEquals("xxx", CoordinateCensor.censorAllNumbers("42"));
        assertNull(CoordinateCensor.censorAllNumbers(null));
    }

    @Test
    @DisplayName("坐标标签/三元组/坐标对触发全数字替换")
    void labelledCoordinatesCensorAllNumbers() {
        assertEquals("X: xxx, Y: xxx", CoordinateCensor.censorCoordinates("X: 123, Y: 456"));
        assertEquals("coords xxx, xxx, xxx",
                CoordinateCensor.censorCoordinates("coords 1, 2, 3"));
        assertEquals("chunk xxx", CoordinateCensor.censorCoordinates("chunk 5"));
        assertEquals("C xxx", CoordinateCensor.censorCoordinates("C 5"));
        assertEquals("xxx, xxx, xxx", CoordinateCensor.censorCoordinates("1, 2, 3"));
        assertEquals("xxx,xxx", CoordinateCensor.censorCoordinates("1,2"));
    }

    @Test
    @DisplayName("普通文本只替换五位以上数字，短数字保持不变")
    void unlabelledTextOnlyReplacesLongNumbers() {
        assertEquals("base xxx", CoordinateCensor.censorCoordinates("base 12345"));
        assertEquals("year 2026", CoordinateCensor.censorCoordinates("year 2026"));
        assertEquals("no digits", CoordinateCensor.censorCoordinates("no digits"));
        assertNull(CoordinateCensor.censorCoordinates(null));
    }

    @Test
    @DisplayName("信息屏裸数字行按坐标处理")
    void infoDisplayBareNumber() {
        assertEquals("xxx", CoordinateCensor.censorInfoDisplay("123"));
        assertEquals(" xxx ", CoordinateCensor.censorInfoDisplay(" -123.5 "));
        assertEquals("xxx,xxx", CoordinateCensor.censorInfoDisplay("123,456"));
        assertEquals("hello xxx", CoordinateCensor.censorInfoDisplay("hello 12345"));
        assertEquals("year 2026", CoordinateCensor.censorInfoDisplay("year 2026"));
        assertNull(CoordinateCensor.censorInfoDisplay(null));
    }

    @Test
    @DisplayName("F3 行：XYZ/Block/Chunk/Facing 前缀触发全数字替换")
    void debugLinePrefixes() {
        assertEquals("XYZ: xxx xxx xxx", CoordinateCensor.censorDebugLine("XYZ: 1 2 3"));
        assertEquals("Block: xxx xxx xxx", CoordinateCensor.censorDebugLine("Block: 1 2 3"));
        assertEquals("Chunk: xxx xxx", CoordinateCensor.censorDebugLine("Chunk: 1 2"));
        assertEquals("Facing: north (Towards negative Z) (xxx / xxx)",
                CoordinateCensor.censorDebugLine("Facing: north (Towards negative Z) (0.0 / 1.0)"));
        assertEquals("§aXYZ: xxx xxx xxx", CoordinateCensor.censorDebugLine("§aXYZ: 1 2 3"));
        assertEquals("hello xxx", CoordinateCensor.censorDebugLine("hello 12345"));
        assertNull(CoordinateCensor.censorDebugLine(null));
    }

    @Test
    @DisplayName("地图文本与坐标脱敏一致")
    void mapTextUsesCoordinateRules() {
        assertEquals(CoordinateCensor.censorCoordinates("X: 123, Y: 456"),
                CoordinateCensor.censorMapText("X: 123, Y: 456"));
        assertEquals(CoordinateCensor.censorCoordinates("base 12345"),
                CoordinateCensor.censorMapText("base 12345"));
        assertNull(CoordinateCensor.censorMapText(null));
    }
}
