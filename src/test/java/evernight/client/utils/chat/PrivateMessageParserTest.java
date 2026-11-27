package evernight.client.utils.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateMessageParserTest {
    @Test
    @DisplayName("六种普通私聊格式解析 sender/content")
    void plainWhisperFormats() {
        assertMessage("From Steve: hello", "Steve", "hello");
        assertMessage("from Steve: hello", "Steve", "hello");
        assertMessage("Steve whispers: hello", "Steve", "hello");
        assertMessage("Steve whispers to you: hello", "Steve", "hello");
        assertMessage("Steve -> me hello", "Steve", "hello");
        assertMessage("[Steve -> me] hello", "Steve", "hello");
    }

    @Test
    @DisplayName("插件 emoji 格式支持装饰名并 trim")
    void emojiWhisperFormat() {
        assertMessage("📨 [VIP] Steve ➡ hello", "[VIP] Steve", "hello");
        assertMessage("📨  Steve  ➡  hi  ", "Steve", "hi");
        assertMessage("📨 X ➡ y", "X", "y");
    }

    @Test
    @DisplayName("公开聊天、短名、缺分隔符不识别为私聊")
    void publicChatIsNotAWhisper() {
        assertNull(PrivateMessageParser.parse("Steve says: hello"));
        assertNull(PrivateMessageParser.parse("From Al: hi"));
        assertNull(PrivateMessageParser.parse("Steve whispers to you:"));
        assertNull(PrivateMessageParser.parse("Steve -> me"));
        assertNull(PrivateMessageParser.parse("📨 Steve hello"));
        assertNull(PrivateMessageParser.parse("Steve whispers:"));
    }

    @Test
    @DisplayName("null/空串返回 null")
    void nullAndEmptyAreSafe() {
        assertNull(PrivateMessageParser.parse(null));
        assertNull(PrivateMessageParser.parse(""));
    }

    @Test
    @DisplayName("空内容仍解析，但 isBlank 为 true")
    void blankContentIsFlagged() {
        PrivateMessageParser.PrivateMessage message = PrivateMessageParser.parse("From Steve: ");
        assertEquals("Steve", message.sender());
        assertEquals("", message.content());
        assertTrue(message.isBlank());

        assertTrue(new PrivateMessageParser.PrivateMessage(null, "x").isBlank());
        assertTrue(new PrivateMessageParser.PrivateMessage("Steve", null).isBlank());
        assertTrue(new PrivateMessageParser.PrivateMessage(" ", "x").isBlank());
        assertFalse(new PrivateMessageParser.PrivateMessage("Steve", "hi").isBlank());
    }

    private static void assertMessage(String text, String sender, String content) {
        PrivateMessageParser.PrivateMessage message = PrivateMessageParser.parse(text);
        assertEquals(sender, message.sender(), text);
        assertEquals(content, message.content(), text);
        assertFalse(message.isBlank(), text);
    }
}
