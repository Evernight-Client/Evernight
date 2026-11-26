/*
 * Trimmed copy of PinIn (https://github.com/Towdium/PinIn, MIT, Copyright (c) 2019 Juntong Liu).
 * The immutable/storage helpers used by the removed indexed searchers were dropped.
 */
package me.towdium.pinin.utils;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Compact bit set of accepted query lengths for one character position.
 */
public class IndexSet {
    public static final IndexSet ZERO = new IndexSet(0x1);
    public static final IndexSet ONE = new IndexSet(0x2);
    public static final IndexSet NONE = new IndexSet(0x0);

    private int value;

    public IndexSet() {
        this(0x0);
    }

    public IndexSet(int value) {
        this.value = value;
    }

    public IndexSet(IndexSet other) {
        this.value = other.value;
    }

    public void set(int index) {
        this.value |= 0x1 << index;
    }

    public boolean get(int index) {
        return (this.value & (0x1 << index)) != 0;
    }

    public void merge(IndexSet other) {
        this.value = this.value == 0x1 ? other.value : (this.value | other.value);
    }

    public boolean traverse(IntPredicate predicate) {
        int bits = this.value;
        for (int index = 0; index < 7; index++) {
            if ((bits & 0x1) == 0x1 && predicate.test(index)) {
                return true;
            }
            if (bits == 0) {
                return false;
            }
            bits >>= 1;
        }
        return false;
    }

    public void foreach(IntConsumer consumer) {
        int bits = this.value;
        for (int index = 0; index < 7; index++) {
            if ((bits & 0x1) == 0x1) {
                consumer.accept(index);
            } else if (bits == 0) {
                return;
            }
            bits >>= 1;
        }
    }

    public void offset(int amount) {
        this.value <<= amount;
    }

    public boolean isEmpty() {
        return this.value == 0x0;
    }

    public IndexSet copy() {
        return new IndexSet(this.value);
    }
}
