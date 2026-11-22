package evernight.client.utils.world;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Display-only coordinate redaction shared by the coordinate hider module and its mixins.
 *
 * <p>A number with five or more digits is treated as a coordinate when scanning unrestricted
 * text. Surfaces that are known to show coordinates are redacted more aggressively.
 */
public final class CoordinateCensor {
    public static final String REDACTED = "xxx";

    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\u00a7\\w])-?\\d{5,}(?:\\.\\d+)?");
    private static final Pattern NUMBER = Pattern.compile("(?<!\\u00a7)-?\\d+(?:\\.\\d+)?");
    private static final Pattern COORDINATE_LABEL = Pattern.compile(
            "(?i)(?:\\b[xyz]o?\\s*[:=]\\s*-?\\d)"
                    + "|(?:\\b(?:coords?|coordinates?|chunk|position|section-relative|facing)\\b[^\\n]*?-?\\d)"
                    + "|(?:\\bC\\s+-?\\d)");
    private static final Pattern DEBUG_PREFIX = Pattern.compile(
            "(?i)^(?:\\u00a7.)?(?:XYZ|Block|Chunk|Facing|Section-relative|Targeted Block):");
    private static final Pattern COORDINATE_TRIPLE = Pattern.compile(
            "^\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*,?\\s*$");
    private static final Pattern COORDINATE_PAIR = Pattern.compile(
            "^\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*,?\\s*$");
    private static final Pattern BARE_NUMBER = Pattern.compile("^\\s*-?\\d+(?:\\.\\d+)?\\s*,?\\s*$");
    private static final Pattern BASE64_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9+/=_-])([A-Za-z0-9+/_-]{12,}={0,2})(?![A-Za-z0-9+/=_-])");

    private CoordinateCensor() {
    }

    /**
     * Whether the text contains a number with at least five digits, the agreed coordinate marker.
     */
    public static boolean containsCoordinate(String text) {
        return text != null && LONG_NUMBER.matcher(text).find();
    }

    /**
     * Whether the text contains a token that looks like Base64 encoded content.
     */
    public static boolean containsBase64(String text) {
        if (text == null) {
            return false;
        }
        Matcher matcher = BASE64_TOKEN.matcher(text);
        while (matcher.find()) {
            if (isLikelyBase64(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces every number in the text with {@link #REDACTED} while leaving formatting codes intact.
     */
    public static String censorAllNumbers(String text) {
        if (text == null) {
            return null;
        }
        return NUMBER.matcher(text).replaceAll(REDACTED);
    }

    /**
     * Redacts coordinate-like text: labelled coordinates have all values replaced, other text only
     * has numbers with five or more digits replaced.
     */
    public static String censorCoordinates(String text) {
        if (text == null) {
            return null;
        }
        if (looksLikeCoordinates(text)) {
            return censorAllNumbers(text);
        }
        return LONG_NUMBER.matcher(text).replaceAll(REDACTED);
    }

    /**
     * Redacts a single line of a Xaero info display. Coordinate displays split long values over
     * several lines, so bare numeric lines are part of the coordinate block as well.
     */
    public static String censorInfoDisplay(String text) {
        if (text == null) {
            return null;
        }
        if (looksLikeCoordinates(text) || BARE_NUMBER.matcher(text.trim()).matches()) {
            return censorAllNumbers(text);
        }
        return LONG_NUMBER.matcher(text).replaceAll(REDACTED);
    }

    /**
     * Redacts one F3 debug screen line.
     */
    public static String censorDebugLine(String text) {
        if (text == null) {
            return null;
        }
        if (DEBUG_PREFIX.matcher(text).find() || COORDINATE_LABEL.matcher(text).find()) {
            return censorAllNumbers(text);
        }
        return LONG_NUMBER.matcher(text).replaceAll(REDACTED);
    }

    /**
     * Redacts text drawn on a Xaero world map overlay.
     */
    public static String censorMapText(String text) {
        return censorCoordinates(text);
    }

    private static boolean looksLikeCoordinates(String text) {
        String trimmed = text.trim();
        return COORDINATE_LABEL.matcher(trimmed).find()
                || DEBUG_PREFIX.matcher(trimmed).find()
                || COORDINATE_TRIPLE.matcher(trimmed).matches()
                || COORDINATE_PAIR.matcher(trimmed).matches();
    }

    private static boolean isLikelyBase64(String token) {
        int length = token.length();
        if (length < 16 || length % 4 != 0) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (int index = 0; index < length; index++) {
            char character = token.charAt(index);
            if (Character.isUpperCase(character)) {
                hasUpper = true;
            } else if (Character.isLowerCase(character)) {
                hasLower = true;
            } else if (Character.isDigit(character)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        if (!hasUpper || !hasLower) {
            return false;
        }
        return hasDigit || hasSymbol;
    }
}
