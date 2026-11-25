package evernight.client.utils.chat;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Expands a random tag inside a template before it is sent to the server.
 *
 * <p>The configurable request command often needs to look different on every send so server-side
 * duplicate-message filters do not drop it. The placeholder {@value #TOKEN} (and its ASCII alias
 * {@value #ASCII_ALIAS}) is replaced with {@value #RANDOM_LENGTH} random ASCII letters or digits,
 * freshly generated for every occurrence and every call.
 *
 * <p>The replacer is stateless; callers pass the raw template and send the returned string with
 * {@link ChatUtils#sendChatOrCommand(String)}.
 */
public final class RandomTagReplacer {
    public static final String TOKEN = "<随机>";
    public static final String ASCII_ALIAS = "<random>";
    public static final int RANDOM_LENGTH = 5;

    private static final char[] RANDOM_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private RandomTagReplacer() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * @return whether the template contains the random tag in either spelling
     */
    public static boolean containsTag(String template) {
        return template != null && (template.contains(TOKEN) || template.contains(ASCII_ALIAS));
    }

    /**
     * Replaces every random tag with a new random string.
     *
     * @param template raw command or chat message; returned unchanged when null, empty or untagged
     * @return the expanded template
     */
    public static String apply(String template) {
        if (template == null || template.isEmpty() || !containsTag(template)) {
            return template;
        }
        StringBuilder expanded = new StringBuilder(template.length() + RANDOM_LENGTH);
        int cursor = 0;
        while (cursor < template.length()) {
            int chineseIndex = template.indexOf(TOKEN, cursor);
            int asciiIndex = template.indexOf(ASCII_ALIAS, cursor);
            int tagIndex;
            int tagLength;
            if (chineseIndex < 0) {
                tagIndex = asciiIndex;
                tagLength = ASCII_ALIAS.length();
            } else if (asciiIndex < 0) {
                tagIndex = chineseIndex;
                tagLength = TOKEN.length();
            } else if (chineseIndex <= asciiIndex) {
                tagIndex = chineseIndex;
                tagLength = TOKEN.length();
            } else {
                tagIndex = asciiIndex;
                tagLength = ASCII_ALIAS.length();
            }
            if (tagIndex < 0) {
                expanded.append(template, cursor, template.length());
                break;
            }
            expanded.append(template, cursor, tagIndex);
            expanded.append(randomTag());
            cursor = tagIndex + tagLength;
        }
        return expanded.toString();
    }

    private static String randomTag() {
        char[] tag = new char[RANDOM_LENGTH];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < tag.length; index++) {
            tag[index] = RANDOM_CHARACTERS[random.nextInt(RANDOM_CHARACTERS.length)];
        }
        return new String(tag);
    }
}
