package evernight.client.utils.chat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a rendered private-message chat line into the sender and the message body.
 *
 * <p>Servers decorate whispers differently, so the parser tries the envelope emoji form used by
 * plugins ({@code 📨 Name ➡ text}) first and then a small list of plain whisper formats. Only formats
 * that cannot be produced by public chat are accepted: a broadcast line such as {@code Name says: ...}
 * is deliberately not treated as a private message.
 *
 * <p>Every pattern captures the sender in group 1 and the content in group 2. The parser is stateless
 * and works on the plain text of a chat component ({@code Component#getString()}).
 */
public final class PrivateMessageParser {
    /**
     * Plugin style whisper: {@code 📨 sender ➡ content}. The sender capture allows decorated names
     * such as {@code [VIP] name}; callers that match names should also split the capture on
     * non-name characters.
     */
    private static final Pattern EMOJI_PRIVATE_MESSAGE = Pattern.compile("^\\s*📨\\s*(.+?)\\s*➡\\s*(.+)$");
    /**
     * Plain whisper formats, narrowed to the incoming direction. All patterns are matched with
     * {@link Matcher#matches()} and expose exactly two groups.
     */
    private static final List<Pattern> WHISPER_PATTERNS = List.of(
            Pattern.compile("^From (\\w{3,16}): (.*)"),
            Pattern.compile("^from (\\w{3,16}): (.*)"),
            Pattern.compile("^(\\w{3,16}) whispers: (.*)"),
            Pattern.compile("^(\\w{3,16}) whispers to you: (.*)"),
            Pattern.compile("^(\\w{3,16}) -> me (.*)"),
            Pattern.compile("^\\[(\\w{3,16}) -> me\\] (.*)"));

    private PrivateMessageParser() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * @param text plain text of a chat line; null or non-whisper lines return null
     * @return the parsed sender and content, or null when the line is not an incoming whisper
     */
    public static PrivateMessage parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher emojiMatcher = EMOJI_PRIVATE_MESSAGE.matcher(text);
        if (emojiMatcher.matches()) {
            return new PrivateMessage(emojiMatcher.group(1).trim(), emojiMatcher.group(2).trim());
        }
        for (Pattern pattern : WHISPER_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches()) {
                return new PrivateMessage(matcher.group(1).trim(), matcher.group(2).trim());
            }
        }
        return null;
    }

    /**
     * One parsed private message.
     *
     * @param sender  name shown by the server; may carry a rank prefix
     * @param content message body, already trimmed
     */
    public record PrivateMessage(String sender, String content) {
        public boolean isBlank() {
            return this.sender == null || this.sender.isBlank()
                    || this.content == null || this.content.isBlank();
        }
    }
}
