package evernight.client.utils.vault;

/**
 * Xoroshiro128++ exactly as Minecraft seeds and draws it for loot-table random sequences.
 *
 * <p>The implementation mirrors {@code XoroshiroRandomSource} and {@code Xoroshiro128PlusPlus}
 * (1.21.11 / 26.1): {@link #nextInt(int)} uses the 32-bit multiply-high algorithm with the same
 * unsigned rejection bound, and {@link #nextFloat()} takes the top 24 bits. These details are
 * load-bearing: a modulo-based or differently rounded bound changes every subsequent draw and
 * desynchronises the whole global vault sequence.
 *
 * <p>One instance is cheap (two longs) and is reused by a single generator thread. It is not
 * thread-safe.
 */
final class VaultRandom {
    /** MD5("minecraft:chests/trial_chambers/reward") split into two big-endian longs. */
    private static final long NORMAL_KEY_LO = 0x102EA793E31F23FFL;
    private static final long NORMAL_KEY_HI = 0xD7C231952CDDADF1L;
    /** MD5("minecraft:chests/trial_chambers/reward_ominous") split into two big-endian longs. */
    private static final long OMINOUS_KEY_LO = 0x05A13D5CE5EDAAB3L;
    private static final long OMINOUS_KEY_HI = 0x1A3950A30A86BC23L;

    private long lo;
    private long hi;
    private StringBuilder trace;

    void startTrace() {
        this.trace = new StringBuilder();
    }

    String trace() {
        return this.trace == null ? "" : this.trace.toString();
    }

    /**
     * Reseeds from the world seed and the vault loot-table id.
     *
     * <p>Vanilla builds {@code RandomSequence(seed, lootTable)} as
     * {@code upgradeSeedTo128bitUnmixed(seed).xor(seedFromHashOf(table)).mixed()}. Both supported
     * game versions hash the id with MD5 (first 16 bytes, two big-endian longs), so the table keys
     * above are constants; only the world seed changes per call.
     */
    void seed(long worldSeed, boolean ominous) {
        long low = worldSeed ^ 0x6A09E667F3BCC909L;
        long high = low + 0x9E3779B97F4A7C15L;
        if (ominous) {
            low ^= OMINOUS_KEY_LO;
            high ^= OMINOUS_KEY_HI;
        } else {
            low ^= NORMAL_KEY_LO;
            high ^= NORMAL_KEY_HI;
        }
        this.lo = mixStafford13(low);
        this.hi = mixStafford13(high);
    }

    void state(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
    }

    long lo() {
        return this.lo;
    }

    long hi() {
        return this.hi;
    }

    long nextLong() {
        long l = this.lo;
        long h = this.hi;
        long result = Long.rotateLeft(l + h, 17) + l;
        h ^= l;
        this.lo = Long.rotateLeft(l, 49) ^ h ^ (h << 21);
        this.hi = Long.rotateLeft(h, 28);
        return result;
    }

    /** {@code RandomSource#nextInt()}: the top 32 bits of one {@link #nextLong()}. */
    int nextInt() {
        return (int) this.nextLong();
    }

    /**
     * {@code RandomSource#nextInt(int)} for an Xoroshiro source.
     *
     * <p>Uses the unsigned 32x32->64 multiply and the rejection window vanilla computes with
     * {@link Integer#remainderUnsigned}. Do not replace this with {@code nextLong() % bound} or
     * {@code Math.floorMod}: either consumes different values and breaks the sequence.
     */
    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long randomBits = Integer.toUnsignedLong(this.nextInt());
        long multiplied = randomBits * bound;
        long fractional = multiplied & 0xFFFFFFFFL;
        if (fractional < bound) {
            int rejectionStart = Integer.remainderUnsigned(~bound + 1, bound);
            while (fractional < rejectionStart) {
                randomBits = Integer.toUnsignedLong(this.nextInt());
                multiplied = randomBits * bound;
                fractional = multiplied & 0xFFFFFFFFL;
            }
        }
        int result = (int) (multiplied >>> 32);
        if (this.trace != null) {
            this.trace.append("nextInt(").append(bound).append(")=").append(result).append('\n');
        }
        return result;
    }

    float nextFloat() {
        float result = (this.nextLong() >>> 40) * 5.9604645E-8F;
        if (this.trace != null) {
            this.trace.append("nextFloat()=").append(result).append('\n');
        }
        return result;
    }

    /** {@code RandomSupport#mixStafford13}. */
    private static long mixStafford13(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
