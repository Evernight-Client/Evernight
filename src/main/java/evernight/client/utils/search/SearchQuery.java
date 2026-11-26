package evernight.client.utils.search;

import evernight.client.render.font.TextUtils;
import evernight.client.settings.options.SearchMode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One normalized search input together with the resolved {@link evernight.client.settings.options.SearchMode}.
 *
 * <p>Queries are immutable and owned by the search box that produced them: the two result caches
 * remember every candidate string already tested, so the per-frame layout pass of a list panel only
 * pays for string comparisons the first time a character is typed. A new instance is created when
 * the text or the effective mode changes (see {@link SearchQueryHolder}).
 */
public final class SearchQuery {
    private final String text;
    private final String pinyinText;
    private final SearchMode mode;
    private final Map<String, Boolean> literalMatches = new HashMap<>();
    private final Map<String, Boolean> pinyinMatches = new HashMap<>();

    private SearchQuery(String text, SearchMode mode) {
        this.text = text;
        this.pinyinText = text.replaceAll("\\s+", "");
        this.mode = mode;
    }

    public static SearchQuery of(String rawText) {
        return of(rawText, SearchMode.effective());
    }

    public static SearchQuery of(String rawText, SearchMode mode) {
        SearchMode resolved = mode == null || mode == SearchMode.AUTO ? SearchMode.effective() : mode;
        return new SearchQuery(normalize(rawText), resolved);
    }

    private static String normalize(String rawText) {
        if (rawText == null) {
            return "";
        }
        return TextUtils.normalizeKeyboardLayout(rawText.trim()).toLowerCase(Locale.ROOT);
    }

    public boolean isBlank() {
        return this.text.isEmpty();
    }

    public String text() {
        return this.text;
    }

    public SearchMode mode() {
        return this.mode;
    }

    /**
     * @return whether any candidate contains the query literally (case-insensitive)
     */
    public boolean matchesEnglish(String... candidates) {
        for (String candidate : candidates) {
            if (this.literalMatch(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether any candidate contains the query literally or matches it as smart pinyin:
     *         full pinyin, initials, mixed Chinese/pinyin, or literal characters
     */
    public boolean matchesChinese(String... candidates) {
        for (String candidate : candidates) {
            if (this.literalMatch(candidate) || this.pinyinMatch(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean literalMatch(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        return this.literalMatches.computeIfAbsent(candidate, value ->
                value.toLowerCase(Locale.ROOT).contains(this.text));
    }

    private boolean pinyinMatch(String candidate) {
        if (candidate == null || candidate.isEmpty() || this.pinyinText.isEmpty()) {
            return false;
        }
        return this.pinyinMatches.computeIfAbsent(candidate, value ->
                PinyinMatcher.contains(value, this.pinyinText));
    }
}
