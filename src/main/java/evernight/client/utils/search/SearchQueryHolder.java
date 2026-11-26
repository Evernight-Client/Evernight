package evernight.client.utils.search;

import evernight.client.settings.options.SearchMode;

import java.util.Objects;

/**
 * Caches the {@link SearchQuery} of one search box.
 *
 * <p>Layout code asks for the query on every frame; the cached instance is only rebuilt when the
 * typed text or the effective mode changes, which also keeps the per-candidate match caches in
 * {@link SearchQuery} alive across frames.
 */
public final class SearchQueryHolder {
    private String text;
    private SearchMode mode;
    private SearchQuery query;

    public SearchQuery update(String rawText) {
        SearchMode effectiveMode = SearchMode.effective();
        if (this.query == null || !Objects.equals(this.text, rawText) || this.mode != effectiveMode) {
            this.text = rawText;
            this.mode = effectiveMode;
            this.query = SearchQuery.of(rawText, effectiveMode);
        }
        return this.query;
    }
}
