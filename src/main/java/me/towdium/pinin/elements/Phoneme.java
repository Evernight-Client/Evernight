/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 * The double-pinyin key layouts were removed; the plain quanyin splitter and the fuzzy-spelling
 * options (zh/z, sh/s, ch/c, ang/an, eng/en, ing/in, v/u) are kept.
 */
package me.towdium.pinin.elements;

import me.towdium.pinin.PinIn;
import me.towdium.pinin.utils.IndexSet;

import java.util.Collections;
import java.util.HashSet;

/**
 * One segment of a pinyin reading, for example the initial {@code zh}, the final {@code ong} or the
 * tone digit. With fuzzy spellings enabled it also accepts the confused variants of that segment.
 */
public class Phoneme implements Element {
    private String[] spellings = {""};

    public Phoneme(String reading, PinIn pinin) {
        this.reload(reading, pinin);
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

    /**
     * Rebuilds the accepted spellings of this phoneme from the fuzzy options of the context.
     */
    public void reload(String reading, PinIn pinin) {
        if (reading.isEmpty()) {
            this.spellings = new String[]{""};
            return;
        }
        HashSet<String> variants = new HashSet<>();
        variants.add(reading);
        if (pinin.fCh2C() && reading.startsWith("c")) Collections.addAll(variants, "c", "ch");
        if (pinin.fSh2S() && reading.startsWith("s")) Collections.addAll(variants, "s", "sh");
        if (pinin.fZh2Z() && reading.startsWith("z")) Collections.addAll(variants, "z", "zh");
        if (pinin.fU2V() && reading.startsWith("v")) variants.add("u" + reading.substring(1));
        if ((pinin.fAng2An() && reading.endsWith("ang"))
                || (pinin.fEng2En() && reading.endsWith("eng"))
                || (pinin.fIng2In() && reading.endsWith("ing"))) {
            variants.add(reading.substring(0, reading.length() - 1));
        }
        if ((pinin.fAng2An() && reading.endsWith("an"))
                || (pinin.fEng2En() && reading.endsWith("en"))
                || (pinin.fIng2In() && reading.endsWith("in"))) {
            variants.add(reading + 'g');
        }
        this.spellings = variants.stream().map(pinin.keyboard()::keys).toArray(String[]::new);
    }
}
