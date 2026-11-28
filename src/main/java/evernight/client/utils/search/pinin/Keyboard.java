/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 * Only the standard quanyin splitter is kept; the shuangpin/ziranma/... key layouts were removed
 * because Evernight only needs plain pinyin, initials and mixed Chinese/English queries.
 */
package evernight.client.utils.search.pinin;

import evernight.client.utils.search.pinin.elements.Pinyin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Splits a pinyin reading into the phonemes the matcher compares against.
 */
public final class Keyboard {
    public static final Keyboard QUANPIN = new Keyboard();

    private Keyboard() {
    }

    public Collection<String> split(String reading) {
        return standard(reading);
    }

    /** Maps one phoneme spelling to the keys that should be matched; quanyin uses identity. */
    public String keys(String spelling) {
        return spelling;
    }

    /**
     * Splits {@code reading} (a quanyin word with a trailing tone digit, e.g. {@code zhong1}) into
     * initial, final and tone. Two-letter initials such as {@code zh} are kept together.
     */
    public static List<String> standard(String reading) {
        List<String> segments = new ArrayList<>(3);
        int cursor = 0;
        if (Pinyin.hasInitial(reading)) {
            cursor = reading.length() > 2 && reading.charAt(1) == 'h' ? 2 : 1;
            segments.add(reading.substring(0, cursor));
        }
        if (reading.length() != cursor + 1) {
            segments.add(reading.substring(cursor, reading.length() - 1));
        }
        segments.add(reading.substring(reading.length() - 1));
        return segments;
    }
}
