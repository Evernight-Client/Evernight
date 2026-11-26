/*
 * Trimmed copy of PinIn - Java library for Chinese text match using Pinyin.
 * Upstream: https://github.com/Towdium/PinIn (MIT, Copyright (c) 2019 Juntong Liu).
 *
 * Evernight keeps only the index-free immediate matcher ({@link #contains(String, String)}):
 * the indexed searchers, the accelerator, the double-pinyin keyboards and the tone/format helpers
 * were removed, while the fuzzy-spelling options are kept and configurable through {@link #config()}.
 * The supported query forms are the upstream ones: full pinyin, initials, mixed Chinese and pinyin,
 * literal characters, plus the enabled fuzzy spellings (zh/z, sh/s, ch/c, ang/an, eng/en, ing/in, v/u).
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
    private final Cache<String, Phoneme> phonemes = new Cache<>(reading -> new Phoneme(reading, this));
    private final Cache<String, Pinyin> pinyins = new Cache<>(reading -> new Pinyin(reading, this));
    private final Char[] chars = new Char[Character.MAX_VALUE + 1];
    private final Char.Dummy temp = new Char.Dummy();

    private boolean fZh2Z = false;
    private boolean fSh2S = false;
    private boolean fCh2C = false;
    private boolean fAng2An = false;
    private boolean fIng2In = false;
    private boolean fEng2En = false;
    private boolean fU2V = false;

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

    public boolean fZh2Z() {
        return this.fZh2Z;
    }

    public boolean fSh2S() {
        return this.fSh2S;
    }

    public boolean fCh2C() {
        return this.fCh2C;
    }

    public boolean fAng2An() {
        return this.fAng2An;
    }

    public boolean fIng2In() {
        return this.fIng2In;
    }

    public boolean fEng2En() {
        return this.fEng2En;
    }

    public boolean fU2V() {
        return this.fU2V;
    }

    /**
     * @return a mutable configuration snapshot; call {@link Config#commit()} to apply it
     */
    public Config config() {
        return new Config();
    }

    private void applyConfig(Config config) {
        this.fZh2Z = config.fZh2Z;
        this.fSh2S = config.fSh2S;
        this.fCh2C = config.fCh2C;
        this.fAng2An = config.fAng2An;
        this.fIng2In = config.fIng2In;
        this.fEng2En = config.fEng2En;
        this.fU2V = config.fU2V;
        this.phonemes.foreach((reading, phoneme) -> phoneme.reload(reading, this));
    }

    /** Fuzzy-spelling options, mirroring the upstream PinIn configuration. */
    public final class Config {
        private boolean fZh2Z = PinIn.this.fZh2Z;
        private boolean fSh2S = PinIn.this.fSh2S;
        private boolean fCh2C = PinIn.this.fCh2C;
        private boolean fAng2An = PinIn.this.fAng2An;
        private boolean fIng2In = PinIn.this.fIng2In;
        private boolean fEng2En = PinIn.this.fEng2En;
        private boolean fU2V = PinIn.this.fU2V;

        private Config() {
        }

        public Config fZh2Z(boolean value) {
            this.fZh2Z = value;
            return this;
        }

        public Config fSh2S(boolean value) {
            this.fSh2S = value;
            return this;
        }

        public Config fCh2C(boolean value) {
            this.fCh2C = value;
            return this;
        }

        public Config fAng2An(boolean value) {
            this.fAng2An = value;
            return this;
        }

        public Config fIng2In(boolean value) {
            this.fIng2In = value;
            return this;
        }

        public Config fEng2En(boolean value) {
            this.fEng2En = value;
            return this;
        }

        public Config fU2V(boolean value) {
            this.fU2V = value;
            return this;
        }

        public PinIn commit() {
            PinIn.this.applyConfig(this);
            return PinIn.this;
        }
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
