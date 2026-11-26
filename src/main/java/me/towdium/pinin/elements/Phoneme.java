/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 * The runtime fuzzy-spelling options and the double-pinyin key layouts were removed; a reading is
 * always matched through its plain quanyin spelling produced by {@link me.towdium.pinin.Keyboard}.
 */
package me.towdium.pinin.elements;

import me.towdium.pinin.utils.IndexSet;

/**
 * One segment of a pinyin reading, for example the initial {@code zh}, the final {@code ong} or the
 * tone digit.
 */
public class Phoneme implements Element {
    private String[] spellings;

    public Phoneme(String reading) {
        this.reload(reading);
    }

    public IndexSet match(String query, IndexSet active, int start, boolean partial) {
        if (this.spellings.length == 1 && this.spellings[0].isEmpty()) {
            return new IndexSet(active);
        }
        IndexSet merged = new IndexSet();
        active.foreach(offset -> {
            IndexSet matched = this.match(query, start + offset, partial);
            matched.offset(offset);
            merged.merge(matched);
        });
        return merged;
    }

    public boolean matchSequence(char character) {
        for (String spelling : this.spellings) {
            if (spelling.charAt(0) == character) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IndexSet match(String query, int start, boolean partial) {
        IndexSet matches = new IndexSet();
        if (this.spellings.length == 1 && this.spellings[0].isEmpty()) {
            return matches;
        }
        for (String spelling : this.spellings) {
            int size = commonPrefixLength(query, spelling, start);
            if (partial && start + size == query.length()) {
                matches.set(size);
            } else if (size == spelling.length()) {
                matches.set(size);
            }
        }
        return matches;
    }

    private static int commonPrefixLength(String query, String spelling, int start) {
        int length = Math.min(query.length() - start, spelling.length());
        for (int index = 0; index < length; index++) {
            if (query.charAt(index + start) != spelling.charAt(index)) {
                return index;
            }
        }
        return length;
    }

    public void reload(String reading) {
        this.spellings = new String[]{reading};
    }
}
