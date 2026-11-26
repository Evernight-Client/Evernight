package evernight.client.utils.search;

import evernight.client.settings.options.SearchMode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The searchable names of one entry, split by language.
 *
 * <p>{@code english} holds English names and registry ids, {@code chinese} holds Chinese names
 * (which additionally match through smart pinyin). Keeping the two sets apart is what makes the
 * configured {@link SearchMode} meaningful: Chinese mode never matches English ids and English mode
 * never matches Chinese names.
 */
public final class SearchKey {
    private static final SearchKey EMPTY = new SearchKey(new String[0], new String[0]);

    private final String[] english;
    private final String[] chinese;
    private String[] allTexts;

    private SearchKey(String[] english, String[] chinese) {
        this.english = english;
        this.chinese = chinese;
    }

    public static SearchKey of(Collection<String> english, Collection<String> chinese) {
        return new SearchKey(distinct(english), distinct(chinese));
    }

    public static SearchKey empty() {
        return EMPTY;
    }

    private static String[] distinct(Collection<String> candidates) {
        Set<String> values = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                values.add(candidate);
            }
        }
        return values.toArray(new String[0]);
    }

    public boolean matches(SearchQuery query) {
        if (query.isBlank()) {
            return true;
        }
        return query.mode() == SearchMode.CHINESE
                ? query.matchesChinese(this.chinese)
                : query.matchesEnglish(this.english);
    }

    public String[] english() {
        return this.english;
    }

    public String[] chinese() {
        return this.chinese;
    }

    /** Every known name, used by the exact and regex query forms of the chest search bar. */
    public String[] allTexts() {
        if (this.allTexts == null) {
            Set<String> values = new LinkedHashSet<>();
            for (String value : this.english) {
                values.add(value);
            }
            for (String value : this.chinese) {
                values.add(value);
            }
            this.allTexts = values.toArray(new String[0]);
        }
        return this.allTexts;
    }
}
