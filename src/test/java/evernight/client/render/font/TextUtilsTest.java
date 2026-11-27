package evernight.client.render.font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUtilsTest {
    @Test
    @DisplayName("带引号参数拆分保留引号内空格")
    void splitsQuotedArguments() {
        assertArrayEquals(new String[] {"a", "b c", "d"},
                TextUtils.splitQuotedArguments("a \"b c\" d"));
        assertArrayEquals(new String[] {"a", "b"}, TextUtils.splitQuotedArguments("a  b"));
        assertArrayEquals(new String[] {"ab cd"},
                TextUtils.splitQuotedArguments("a\"b c\"d"));
        assertArrayEquals(new String[] {"a", "b"}, TextUtils.splitQuotedArguments("a \"b"));
        assertArrayEquals(new String[0], TextUtils.splitQuotedArguments(""));
        assertArrayEquals(new String[0], TextUtils.splitQuotedArguments("   "));
        assertArrayEquals(new String[0], TextUtils.splitQuotedArguments("\"\""));
    }

    @Test
    @DisplayName("按换行拆分保留空段与结尾空段")
    void splitsOnNewlines() {
        assertArrayEquals(new String[] {"a", "b"}, TextUtils.splitOnDelimiter("a\nb"));
        assertArrayEquals(new String[] {"a", ""}, TextUtils.splitOnDelimiter("a\n"));
        assertArrayEquals(new String[] {"a", "", "b"}, TextUtils.splitOnDelimiter("a\n\nb"));
        assertArrayEquals(new String[] {""}, TextUtils.splitOnDelimiter(""));
        assertArrayEquals(new String[] {"", ""}, TextUtils.splitOnDelimiter("\n"));
    }

    @Test
    @DisplayName("最后一个 § 格式码；无格式码返回空格")
    void lastFormattingCode() {
        assertEquals(Character.valueOf('a'), TextUtils.getLastFormattingCode("§aHello"));
        assertEquals(Character.valueOf('b'), TextUtils.getLastFormattingCode("§aHello§bWorld"));
        assertEquals(Character.valueOf(' '), TextUtils.getLastFormattingCode("Hello"));
        assertEquals(Character.valueOf(' '), TextUtils.getLastFormattingCode("§"));
    }

    @Test
    @DisplayName("忽略大小写的包含判断")
    void containsIgnoreCase() {
        assertTrue(TextUtils.containsIgnoreCase("Hello World", "ELL"));
        assertTrue(TextUtils.containsIgnoreCase("Hello", "hello"));
        assertFalse(TextUtils.containsIgnoreCase("Hello", "xyz"));
    }
}
