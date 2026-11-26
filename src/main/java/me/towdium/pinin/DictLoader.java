/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 */
package me.towdium.pinin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * Loads the {@code character: reading, reading} dictionary that {@link PinIn} indexes at startup.
 */
@FunctionalInterface
public interface DictLoader {
    /** Bundled dictionary shipped with Evernight (derived from rime-terra-pinyin and pinyin-data). */
    String DEFAULT_RESOURCE = "/assets/evernight/pinyin/data.txt";

    void load(BiConsumer<Character, String[]> feed);

    /**
     * Reads a dictionary from the classpath. Lines that do not follow the
     * {@code <char>: <reading>[, <reading>]} layout are skipped so a partially edited file cannot
     * break the client at startup.
     */
    final class Classpath implements DictLoader {
        private final String resource;

        public Classpath(String resource) {
            this.resource = resource;
        }

        @Override
        public void load(BiConsumer<Character, String[]> feed) {
            try (InputStream stream = PinIn.class.getResourceAsStream(this.resource)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing bundled pinyin dictionary " + this.resource);
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() < 4 || line.charAt(1) != ':') {
                        continue;
                    }
                    feed.accept(line.charAt(0), line.substring(3).split(", "));
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read bundled pinyin dictionary " + this.resource, failure);
            }
        }
    }
}
