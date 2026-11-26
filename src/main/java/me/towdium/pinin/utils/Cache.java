/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 */
package me.towdium.pinin.utils;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Lazily filled map for the phoneme and pinyin elements of one {@link me.towdium.pinin.PinIn}
 * context. Entries are created on first use; {@link #foreach} lets a configuration change rebuild
 * the already created elements in place.
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

    public void foreach(BiConsumer<K, V> consumer) {
        this.entries.forEach(consumer);
    }
}
