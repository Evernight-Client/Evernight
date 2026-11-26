/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 */
package me.towdium.pinin.utils;

import java.util.HashMap;
import java.util.function.Function;

/**
 * Lazily filled map for the phoneme and pinyin elements of one {@link me.towdium.pinin.PinIn}
 * context. Lookups happen during construction only; matching reads the prepared elements.
 */
public class Cache<K, V> {
    private final HashMap<K, V> entries = new HashMap<>();
    private final Function<K, V> generator;

    public Cache(Function<K, V> generator) {
        this.generator = generator;
    }

    public V get(K key) {
        V cached = this.entries.get(key);
        if (cached == null) {
            cached = this.generator.apply(key);
            if (cached != null) {
                this.entries.put(key, cached);
            }
        }
        return cached;
    }
}
