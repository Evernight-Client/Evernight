package evernight.client.utils.vault;

/**
 * Exact {@code EnchantmentHelper#selectEnchantment} for the vault loot tables.
 *
 * <p>The candidate list for a level is precomputed per (item, effective-level) pair from the
 * 1.21.11 / 26.1 enchantment registry: the {@code #minecraft:on_random_loot} tag in its vanilla
 * tag order, filtered by {@code Enchantment#isPrimaryItem}, with at most one instance per
 * enchantment (the highest level whose {@code min_cost..max_cost} range contains the level). The
 * hot path then only copies that list and performs the same weighted picks and incompatibility
 * pruning vanilla does.
 *
 * <p>Do not reorder {@code ITEM_PRIMARY_ENCHANTMENTS} or {@code ON_RANDOM_LOOT}: weighted picks walk
 * the list in tag order, so a different order changes which enchantment a random draw selects.
 */
final class VaultEnchantments {
    /** Highest effective level a vault enchant-with-levels roll can produce (vanilla max is ~33). */
    private static final int MAX_TABLE_LEVEL = 64;
    private static final int MAX_SELECTED = 32;
    private static final int MAX_ACTIVE = 64;

    private static final Candidate[][] CANDIDATES =
            new Candidate[VaultLootData.ITEM_COUNT][MAX_TABLE_LEVEL + 1];
    private static final long ON_RANDOM_MASK = maskOnRandom();

    static {
        // Small, bounded and deterministic: build every (item, effective level) list once so the
        // scanning threads only read immutable arrays.
        for (int item = 0; item < VaultLootData.ITEM_COUNT; item++) {
            for (int level = 1; level <= MAX_TABLE_LEVEL; level++) {
                CANDIDATES[item][level] = build(item, level);
            }
        }
    }

    private VaultEnchantments() {
    }

    /** Reusable per-generator scratch so the hot path never allocates. */
    static final class Scratch {
        final int[] activeEnchantment = new int[MAX_ACTIVE];
        final int[] activeLevel = new int[MAX_ACTIVE];
        final int[] activeWeight = new int[MAX_ACTIVE];
        final int[] selectedEnchantment = new int[MAX_SELECTED];
        final int[] selectedLevel = new int[MAX_SELECTED];
    }

    private static final class Candidate {
        final int[] enchantment;
        final int[] level;
        final int[] weight;
        final int size;
        final int totalWeight;

        Candidate(int[] enchantment, int[] level, int[] weight, int size, int totalWeight) {
            this.enchantment = enchantment;
            this.level = level;
            this.weight = weight;
            this.size = size;
            this.totalWeight = totalWeight;
        }
    }

    /**
     * Rolls one {@code enchant_with_levels} function and returns how many enchantments were added.
     *
     * <p>Consumes exactly the draws of vanilla: two {@code nextInt(enchantability/4 + 1)}, two
     * {@code nextFloat}, one weighted pick, then one {@code nextInt(50)} per loop iteration and one
     * weighted pick per successful iteration.
     */
    static int select(VaultRandom random, int item, int level, Scratch scratch) {
        int enchantability = VaultLootData.ITEM_ENCHANTABILITY[item];
        int delta = enchantability / 4 + 1;
        level += 1 + random.nextInt(delta) + random.nextInt(delta);
        float amplifier = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        level = Math.max(1, Math.round(level + level * amplifier));

        Candidate candidate = candidates(item, Math.min(level, MAX_TABLE_LEVEL));
        if (candidate.size == 0) {
            return 0;
        }

        int active = candidate.size;
        System.arraycopy(candidate.enchantment, 0, scratch.activeEnchantment, 0, active);
        System.arraycopy(candidate.level, 0, scratch.activeLevel, 0, active);
        System.arraycopy(candidate.weight, 0, scratch.activeWeight, 0, active);

        int totalWeight = candidate.totalWeight;
        int pick = pick(random, scratch, active, totalWeight);
        scratch.selectedEnchantment[0] = scratch.activeEnchantment[pick];
        scratch.selectedLevel[0] = scratch.activeLevel[pick];
        int selected = 1;
        int last = scratch.selectedEnchantment[0];

        while (random.nextInt(50) <= level) {
            long incompatible = VaultLootData.ENCHANTMENT_INCOMPATIBLE[last];
            int kept = 0;
            totalWeight = 0;
            for (int i = 0; i < active; i++) {
                int enchantment = scratch.activeEnchantment[i];
                if ((incompatible & (1L << enchantment)) != 0) {
                    continue;
                }
                scratch.activeEnchantment[kept] = enchantment;
                scratch.activeLevel[kept] = scratch.activeLevel[i];
                scratch.activeWeight[kept] = scratch.activeWeight[i];
                totalWeight += scratch.activeWeight[kept];
                kept++;
            }
            active = kept;
            if (active == 0) {
                break;
            }

            pick = pick(random, scratch, active, totalWeight);
            last = scratch.activeEnchantment[pick];
            scratch.selectedEnchantment[selected] = last;
            scratch.selectedLevel[selected] = scratch.activeLevel[pick];
            selected++;
            level >>= 1;
        }
        return selected;
    }

    private static int pick(VaultRandom random, Scratch scratch, int active, int totalWeight) {
        int weight = random.nextInt(totalWeight);
        for (int i = 0; i < active; i++) {
            weight -= scratch.activeWeight[i];
            if (weight < 0) {
                return i;
            }
        }
        return active - 1;
    }

    private static long maskOnRandom() {
        long mask = 0L;
        for (int enchantment : VaultLootData.ON_RANDOM_LOOT) {
            mask |= 1L << enchantment;
        }
        return mask;
    }

    private static Candidate candidates(int item, int level) {
        Candidate[] row = CANDIDATES[item];
        Candidate cached = row[level];
        if (cached != null) {
            return cached;
        }
        Candidate built = build(item, level);
        row[level] = built;
        return built;
    }

    private static Candidate build(int item, int level) {
        long applicable = item == VaultLootData.ITEM_BOOK
                ? ON_RANDOM_MASK
                : VaultLootData.ITEM_PRIMARY_ENCHANTMENTS[item];
        int[] enchantment = new int[VaultLootData.ENCHANTMENT_COUNT];
        int[] enchantmentLevel = new int[VaultLootData.ENCHANTMENT_COUNT];
        int[] weight = new int[VaultLootData.ENCHANTMENT_COUNT];
        int size = 0;
        int totalWeight = 0;
        for (int enchantmentId : VaultLootData.ON_RANDOM_LOOT) {
            if ((applicable & (1L << enchantmentId)) == 0) {
                continue;
            }
            int maxLevel = VaultLootData.ENCHANTMENT_MAX_LEVEL[enchantmentId];
            for (int candidateLevel = maxLevel; candidateLevel >= 1; candidateLevel--) {
                int minCost = VaultLootData.ENCHANTMENT_MIN_BASE[enchantmentId]
                        + VaultLootData.ENCHANTMENT_MIN_PER_LEVEL[enchantmentId] * (candidateLevel - 1);
                int maxCost = VaultLootData.ENCHANTMENT_MAX_BASE[enchantmentId]
                        + VaultLootData.ENCHANTMENT_MAX_PER_LEVEL[enchantmentId] * (candidateLevel - 1);
                if (level >= minCost && level <= maxCost) {
                    enchantment[size] = enchantmentId;
                    enchantmentLevel[size] = candidateLevel;
                    weight[size] = VaultLootData.ENCHANTMENT_WEIGHT[enchantmentId];
                    totalWeight += weight[size];
                    size++;
                    break;
                }
            }
        }
        return new Candidate(enchantment, enchantmentLevel, weight, size, totalWeight);
    }
}
