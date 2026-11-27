package evernight.client.utils.vault;

/**
 * Stable 64-bit fingerprint of the item components that the vault tables can produce.
 *
 * <p>Prediction only needs item + count for the public targets, but detection compares a whole
 * observed opening. Two enchanted books of the same count but different enchantments must not match,
 * so the core folds potion, ominous-bottle amplifier, damage and selected enchantments into one
 * value. The same recipe is applied to observed {@code ItemStack}s by the capture adapter; keep both
 * sides in sync.
 *
 * <p>Enchantment terms are added, not XORed or ordered, because {@code ItemEnchantments} equality is
 * order independent. A zero hash means "no components" and is never returned for a stack that has
 * any, so {@code 0} is a safe "ignore components" sentinel.
 */
final class VaultComponentHash {
    private VaultComponentHash() {
    }

    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    static long finish(long hash) {
        return hash == 0L ? 1L : hash;
    }

    static long enchantment(int enchantment, int level) {
        return mix(0x100000000L + ((long) enchantment << 8) + level);
    }

    static long potion(int potion) {
        return mix(0x200000000L + potion);
    }

    static long ominousAmplifier(int amplifier) {
        return mix(0x300000000L + amplifier);
    }

    static long damage(int damage) {
        return mix(0x400000000L + damage);
    }
}
