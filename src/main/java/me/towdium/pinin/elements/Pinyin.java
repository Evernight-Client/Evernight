/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 */
package me.towdium.pinin.elements;

import me.towdium.pinin.PinIn;
import me.towdium.pinin.utils.IndexSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One full pinyin reading of a character, split into initial, final and tone by
 * {@link me.towdium.pinin.Keyboard}. A query may skip trailing phonemes, which is what makes
 * initials such as {@code zhg} for {@code zhong guo} work.
 */
public class Pinyin implements Element {
    private final String raw;
    private Phoneme[] phonemes;

    public Pinyin(String reading, PinIn pinin) {
        this.raw = reading;
        this.reload(reading, pinin);
    }

    public IndexSet match(String query, int start, boolean partial) {
        IndexSet active = IndexSet.ZERO;
        IndexSet matches = new IndexSet();
        for (Phoneme phoneme : this.phonemes) {
            active = phoneme.match(query, active, start, partial);
            if (active.isEmpty()) {
                break;
            }
            matches.merge(active);
        }
        // Let a character also match the first letter of its reading, so "z" or "zh" work.
        if (this.phonemes[0].matchSequence(query.charAt(start))) {
            matches.set(1);
        }
        return matches;
    }

    @Override
    public String toString() {
        return this.raw;
    }

    public void reload(String reading, PinIn pinin) {
        Collection<String> segments = pinin.keyboard().split(reading);
        List<Phoneme> parts = new ArrayList<>(segments.size());
        for (String segment : segments) {
            parts.add(pinin.getPhoneme(segment));
        }
        this.phonemes = parts.toArray(new Phoneme[0]);
    }

    public static boolean hasInitial(String reading) {
        char first = reading.charAt(0);
        return first != 'a' && first != 'e' && first != 'i' && first != 'o' && first != 'u' && first != 'v';
    }
}
