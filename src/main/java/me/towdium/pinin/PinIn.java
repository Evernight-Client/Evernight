/*
 * Trimmed copy of PinIn - Java library for Chinese text match using Pinyin.
 * Upstream: https://github.com/Towdium/PinIn (MIT, Copyright (c) 2019 Juntong Liu).
 *
 * Evernight keeps only the index-free immediate matcher ({@link #contains(String, String)}):
 * the indexed searchers, the accelerator, the double-pinyin keyboards, the tone/format helpers
 * and the runtime fuzzy-spelling configuration were removed. The matching algorithm itself is
 * unchanged, so the supported query forms are still the upstream ones:
 * full pinyin, initials, mixed Chinese and pinyin, and literal characters.
 */
package me.towdium.pinin;

import me.towdium.pinin.elements.Char;
import me.towdium.pinin.elements.Element;
import me.towdium.pinin.elements.Phoneme;
import me.towdium.pinin.elements.Pinyin;
import me.towdium.pinin.utils.Cache;
import me.towdium.pinin.utils.IndexSet;

/**
 * Pinyin matching context.
 *
 * <p>Construction parses the bundled dictionary once; afterwards {@link #contains(String, String)}
 * matches a candidate against a mixed pinyin/initial query without building any index. Instances are
 * read-only after construction, except for the single scratch {@link Char.Dummy} used for unknown
 * characters, so matching must stay on one thread (the client thread).
 */
public class PinIn {
    private final Cache<String, Phoneme> phonemes = new Cache<>(Phoneme::new);
    private final Cache<String, Pinyin> pinyins = new Cache<>(reading -> new Pinyin(reading, this));
    private final Char[] chars = new Char[Character.MAX_VALUE + 1];
    private final Char.Dummy temp = new Char.Dummy();

    public PinIn() {
        this(new DictLoader.Classpath(DictLoader.DEFAULT_RESOURCE));
    }

    public PinIn(DictLoader loader) {
        loader.load((character, readings) -> {
            if (readings == null) {
                this.chars[character] = null;
                return;
            }
            Pinyin[] candidates = new Pinyin[readings.length];
            for (int index = 0; index < readings.length; index++) {
                candidates[index] = this.getPinyin(readings[index]);
            }
            this.chars[character] = new Char(character, candidates);
        });
    }

    /**
     * @return whether {@code query} matches inside {@code text}, where Chinese characters may be
     *         written as full pinyin, initials, a mix of both, or kept as characters
     */
    public boolean contains(String text, String query) {
        if (text.isEmpty()) {
            return text.contains(query);
        }
        return Matcher.contains(text, query, this);
    }

    public Phoneme getPhoneme(String reading) {
        return this.phonemes.get(reading);
    }

    public Pinyin getPinyin(String reading) {
        return this.pinyins.get(reading);
    }

    public Keyboard keyboard() {
        return Keyboard.QUANPIN;
    }

    public Char getChar(char character) {
        Char known = this.chars[character];
        if (known != null) {
            return known;
        }
        this.temp.set(character);
        return this.temp;
    }

    private static final class Matcher {
        private static boolean contains(String text, String query, PinIn pinin) {
            for (int start = 0; start < text.length(); start++) {
                if (check(text, start, query, 0, pinin)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean check(String text, int textStart, String query, int queryStart, PinIn pinin) {
            if (queryStart == query.length()) {
                return true;
            }
            Element element = pinin.getChar(text.charAt(textStart));
            IndexSet matches = element.match(query, queryStart, true);
            if (textStart == text.length() - 1) {
                return matches.get(query.length() - queryStart);
            }
            return matches.traverse(consumed -> check(text, textStart + 1, query, queryStart + consumed, pinin));
        }
    }
}
