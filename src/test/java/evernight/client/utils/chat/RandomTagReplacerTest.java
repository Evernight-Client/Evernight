package evernight.client.utils.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomTagReplacerTest {
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    @DisplayName("containsTag 识别两种写法，大小写敏感")
    void containsTagRecognizesBothForms() {
        assertTrue(RandomTagReplacer.containsTag("<随机>"));
        assertTrue(RandomTagReplacer.containsTag("<random>"));
        assertTrue(RandomTagReplacer.containsTag("prefix <随机> suffix"));
        assertFalse(RandomTagReplacer.containsTag("<Random>"));
        assertFalse(RandomTagReplacer.containsTag("<rando>"));
        assertFalse(RandomTagReplacer.containsTag(null));
        assertFalse(RandomTagReplacer.containsTag(""));
    }

    @Test
    @DisplayName("无标签/空串/null 原样返回")
    void untaggedTemplatesAreUnchanged() {
        assertNull(RandomTagReplacer.apply(null));
        assertSame("", RandomTagReplacer.apply(""));
        assertEquals("hello world", RandomTagReplacer.apply("hello world"));
        assertEquals("x<rando>y", RandomTagReplacer.apply("x<rando>y"));
    }

    @Test
    @DisplayName("单个中文标签替换为 5 位字母数字")
    void replacesChineseToken() {
        String result = RandomTagReplacer.apply("<随机>");
        assertEquals(RandomTagReplacer.RANDOM_LENGTH, result.length());
        assertRandomCharacters(result);
        assertFalse(RandomTagReplacer.containsTag(result));
    }

    @Test
    @DisplayName("单个 ASCII 标签替换为 5 位字母数字")
    void replacesAsciiToken() {
        String result = RandomTagReplacer.apply("<random>");
        assertEquals(RandomTagReplacer.RANDOM_LENGTH, result.length());
        assertRandomCharacters(result);
        assertFalse(RandomTagReplacer.containsTag(result));
    }

    @Test
    @DisplayName("多个/混合标签都替换，模板其余文本保持")
    void replacesEveryOccurrence() {
        String result = RandomTagReplacer.apply("a<随机>b<random>c");
        assertEquals(13, result.length());
        assertEquals('a', result.charAt(0));
        assertEquals('b', result.charAt(6));
        assertEquals('c', result.charAt(12));
        assertRandomCharacters(result.substring(1, 6));
        assertRandomCharacters(result.substring(7, 12));
        assertFalse(RandomTagReplacer.containsTag(result));

        String adjacent = RandomTagReplacer.apply("<随机><random>");
        assertEquals(10, adjacent.length());
        assertRandomCharacters(adjacent.substring(0, 5));
        assertRandomCharacters(adjacent.substring(5));
        assertFalse(RandomTagReplacer.containsTag(adjacent));
    }

    @Test
    @DisplayName("标签外的 emoji/前缀后缀原样保留")
    void surroundingTextIsPreserved() {
        String result = RandomTagReplacer.apply("🎉<随机>🎉");
        assertEquals("🎉", result.substring(0, 2));
        assertEquals("🎉", result.substring(result.length() - 2));
        assertRandomCharacters(result.substring(2, result.length() - 2));
    }

    @Test
    @DisplayName("多次调用产生新随机值")
    void repeatedCallsAreFresh() {
        Set<String> values = new HashSet<>();
        for (int index = 0; index < 16; index++) {
            values.add(RandomTagReplacer.apply("<随机>"));
        }
        assertTrue(values.size() > 1, "random tags should not be a constant");
    }

    private static void assertRandomCharacters(String value) {
        assertEquals(RandomTagReplacer.RANDOM_LENGTH, value.length());
        for (int index = 0; index < value.length(); index++) {
            assertTrue(CHARSET.indexOf(value.charAt(index)) >= 0, "unexpected character in " + value);
        }
    }
}
