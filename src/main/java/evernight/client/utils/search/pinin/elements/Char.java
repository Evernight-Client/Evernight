/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 */
package evernight.client.utils.search.pinin.elements;

import evernight.client.utils.search.pinin.utils.IndexSet;

/**
 * One source character: matches either literally or through any of its pinyin readings.
 */
public class Char implements Element {
    protected char character;
    public static final Pinyin[] NONE = new Pinyin[0];

    private final Pinyin[] pinyins;

    public Char(char character, Pinyin[] pinyins) {
        this.character = character;
        this.pinyins = pinyins;
    }

    @Override
    public IndexSet match(String query, int start, boolean partial) {
        IndexSet matches = (query.charAt(start) == this.character ? IndexSet.ONE : IndexSet.NONE).copy();
        for (Pinyin pinyin : this.pinyins) {
            matches.merge(pinyin.match(query, start, partial));
        }
        return matches;
    }

    public char get() {
        return this.character;
    }

    public Pinyin[] pinyins() {
        return this.pinyins;
    }

    /** Scratch element for characters the dictionary does not know; only literal matching applies. */
    public static class Dummy extends Char {
        public Dummy() {
            super('\0', NONE);
        }

        public void set(char character) {
            this.character = character;
        }
    }
}
