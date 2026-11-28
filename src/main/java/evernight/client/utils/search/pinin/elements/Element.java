/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 */
package evernight.client.utils.search.pinin.elements;

import evernight.client.utils.search.pinin.utils.IndexSet;

/**
 * A single character position of a candidate string, matched against the query.
 */
public interface Element {
    /**
     * @param query   full search query
     * @param start   index of the query character that has to match this element
     * @param partial whether the query may end in the middle of a reading
     * @return set of accepted query lengths; bit {@code i} means "consumed {@code i} characters"
     */
    IndexSet match(String query, int start, boolean partial);
}
