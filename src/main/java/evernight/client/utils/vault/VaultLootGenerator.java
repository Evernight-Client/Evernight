package evernight.client.utils.vault;

/**
 * Generates one vault opening's drops with the exact 1.21.11 / 26.1 vanilla draw order.
 *
 * <p>The generator is a stateful, single-threaded cursor over a global {@code RandomSequence}. Each
 * call to {@link #generate(int)} advances the sequence by exactly the number of draws vanilla's
 * {@code LootTable#getRandomItems} uses for that opening, then fills the primitive buffers. It never
 * allocates on the hot path.
 *
 * <p>Draw order per opening (matches {@code LootPool#addRandomItems}):
 * <ol>
 *   <li>root pool 0: weighted nested-table pick, then the nested table's one weighted entry pick;</li>
 *   <li>root pool 1: uniform roll count (1..3), then one weighted entry pick per roll;</li>
 *   <li>root pool 2: chance condition ({@code nextFloat}), then one weighted unique entry pick.</li>
 * </ol>
 * Entry functions run in JSON order; {@code enchant_with_levels} is the only data-dependent draw
 * count and is delegated to {@link VaultEnchantments}.
 */
final class VaultLootGenerator {
    private static final int MAX_DROPS = 8;

    private final VaultRandom random = new VaultRandom();
    private final VaultEnchantments.Scratch enchantScratch = new VaultEnchantments.Scratch();
    private final int[] items = new int[MAX_DROPS];
    private final int[] counts = new int[MAX_DROPS];
    private final long[] components = new long[MAX_DROPS];
    private int size;
    private boolean stateOnly;

    void reset(long worldSeed, boolean ominous) {
        this.random.seed(worldSeed, ominous);
    }

    void reset(long lo, long hi) {
        this.random.state(lo, hi);
    }

    long stateLo() {
        return this.random.lo();
    }

    long stateHi() {
        return this.random.hi();
    }

    void startTrace() {
        this.random.startTrace();
    }

    String trace() {
        return this.random.trace();
    }

    int size() {
        return this.size;
    }

    int item(int index) {
        return this.items[index];
    }

    int count(int index) {
        return this.counts[index];
    }

    long components(int index) {
        return this.components[index];
    }

    int generate(int table) {
        this.stateOnly = false;
        this.size = 0;
        this.generateTable(table);
        return this.size;
    }

    /** Advances the sequence without materialising drops; only the draw count matters. */
    void advance(int table) {
        this.stateOnly = true;
        this.size = 0;
        this.generateTable(table);
        this.stateOnly = false;
    }

    private void generateTable(int table) {
        int poolStart = VaultLootData.TABLE_POOL_START[table];
        int poolEnd = poolStart + VaultLootData.TABLE_POOL_COUNT[table];
        for (int pool = poolStart; pool < poolEnd; pool++) {
            float chance = VaultLootData.POOL_CHANCE[pool];
            if (chance >= 0.0F && !(this.random.nextFloat() < chance)) {
                continue;
            }

            int minRolls = VaultLootData.POOL_MIN_ROLLS[pool];
            int maxRolls = VaultLootData.POOL_MAX_ROLLS[pool];
            int rolls = this.nextIntInclusive(minRolls, maxRolls);
            if (rolls <= 0) {
                continue;
            }

            int entryStart = VaultLootData.POOL_ENTRY_START[pool];
            int entryCount = VaultLootData.POOL_ENTRY_COUNT[pool];
            if (entryCount == 0) {
                continue;
            }
            int totalWeight = VaultLootData.POOL_TOTAL_WEIGHT[pool];
            for (int roll = 0; roll < rolls; roll++) {
                int entry = entryStart;
                if (entryCount != 1) {
                    int weight = this.random.nextInt(totalWeight);
                    while (true) {
                        weight -= VaultLootData.ENTRY_WEIGHT[entry];
                        if (weight < 0) {
                            break;
                        }
                        entry++;
                    }
                }
                this.generateEntry(entry);
            }
        }
    }

    private void generateEntry(int entry) {
        int functionStart = VaultLootData.ENTRY_FUNCTION_START[entry];
        int functionCount = VaultLootData.ENTRY_FUNCTION_COUNT[entry];
        if (VaultLootData.ENTRY_ITEM[entry] < 0) {
            // A nested loot table contributes its own drops in place; its pools have their own rolls.
            for (int f = 0; f < functionCount; f++) {
                int function = functionStart + f;
                if (VaultLootData.FUNCTION_TYPE[function] == VaultLootData.FN_NESTED_TABLE) {
                    this.generateTable(VaultLootData.FUNCTION_A[function]);
                }
            }
            return;
        }

        if (this.stateOnly) {
            this.advanceItemEntry(entry, functionStart, functionCount);
            return;
        }

        if (this.size >= MAX_DROPS) {
            // Vanilla vault tables can produce at most 1 + 3 + 1 drops; this is only a safety net.
            return;
        }
        int slot = this.size++;
        int item = VaultLootData.ENTRY_ITEM[entry];
        int count = 1;
        long componentHash = 0L;

        for (int f = 0; f < functionCount; f++) {
            int function = functionStart + f;
            switch (VaultLootData.FUNCTION_TYPE[function]) {
                case VaultLootData.FN_COUNT_UNIFORM -> {
                    int min = VaultLootData.FUNCTION_A[function];
                    int max = VaultLootData.FUNCTION_B[function];
                    count = this.nextIntInclusive(min, max);
                }
                case VaultLootData.FN_COUNT_CONSTANT -> count = VaultLootData.FUNCTION_A[function];
                case VaultLootData.FN_DAMAGE -> {
                    float min = VaultLootData.FUNCTION_A[function] / 10000.0F;
                    float max = VaultLootData.FUNCTION_B[function] / 10000.0F;
                    float rolled = min >= max ? min : min + this.random.nextFloat() * (max - min);
                    int maxDamage = item == VaultLootData.ITEM_SHIELD ? 336 : 0;
                    float remaining = 1.0F - Math.max(0.0F, Math.min(1.0F, rolled));
                    int damage = (int) Math.floor(remaining * maxDamage);
                    componentHash += VaultComponentHash.damage(damage);
                }
                case VaultLootData.FN_POTION -> componentHash +=
                        VaultComponentHash.potion(VaultLootData.FUNCTION_A[function]);
                case VaultLootData.FN_OMINOUS_AMPLIFIER -> {
                    int min = VaultLootData.FUNCTION_A[function];
                    int max = VaultLootData.FUNCTION_B[function];
                    int amplifier = Math.max(0, Math.min(4, this.nextIntInclusive(min, max)));
                    componentHash += VaultComponentHash.ominousAmplifier(amplifier);
                }
                case VaultLootData.FN_ENCHANT_WITH_LEVELS -> {
                    int min = VaultLootData.FUNCTION_B[function];
                    int max = VaultLootData.FUNCTION_C[function];
                    int level = this.nextIntInclusive(min, max);
                    int selected = VaultEnchantments.select(this.random, item, level, this.enchantScratch);
                    if (selected > 0 && item == VaultLootData.ITEM_BOOK) {
                        item = VaultLootData.ITEM_ENCHANTED_BOOK;
                    }
                    componentHash += enchantmentHash(selected);
                }
                case VaultLootData.FN_ENCHANT_RANDOMLY -> {
                    int[] options = VaultLootData.ENCHANT_RANDOM_LISTS[VaultLootData.FUNCTION_A[function]];
                    int enchantment = options[this.random.nextInt(options.length)];
                    int maxLevel = VaultLootData.ENCHANTMENT_MAX_LEVEL[enchantment];
                    int level = this.nextIntInclusive(1, maxLevel);
                    if (item == VaultLootData.ITEM_BOOK) {
                        item = VaultLootData.ITEM_ENCHANTED_BOOK;
                    }
                    componentHash += VaultComponentHash.enchantment(enchantment, level);
                }
                case VaultLootData.FN_SET_ENCHANTMENT -> {
                    int enchantment = VaultLootData.FUNCTION_A[function];
                    int level = VaultLootData.FUNCTION_B[function];
                    if (item == VaultLootData.ITEM_BOOK) {
                        item = VaultLootData.ITEM_ENCHANTED_BOOK;
                    }
                    componentHash += VaultComponentHash.enchantment(enchantment, level);
                }
                default -> {
                    // FN_NESTED_TABLE on an item entry cannot happen; ignore unknown opcodes.
                }
            }
        }

        this.items[slot] = item;
        this.counts[slot] = count;
        this.components[slot] = VaultComponentHash.finish(componentHash);
    }

    /** Executes only the RNG-consuming parts of an item entry, for checkpoint construction. */
    private void advanceItemEntry(int entry, int functionStart, int functionCount) {
        int item = VaultLootData.ENTRY_ITEM[entry];
        for (int f = 0; f < functionCount; f++) {
            int function = functionStart + f;
            switch (VaultLootData.FUNCTION_TYPE[function]) {
                case VaultLootData.FN_COUNT_UNIFORM -> this.nextIntInclusive(
                        VaultLootData.FUNCTION_A[function], VaultLootData.FUNCTION_B[function]);
                case VaultLootData.FN_COUNT_CONSTANT, VaultLootData.FN_POTION,
                     VaultLootData.FN_SET_ENCHANTMENT -> {
                }
                case VaultLootData.FN_DAMAGE -> this.random.nextFloat();
                case VaultLootData.FN_OMINOUS_AMPLIFIER -> this.nextIntInclusive(
                        VaultLootData.FUNCTION_A[function], VaultLootData.FUNCTION_B[function]);
                case VaultLootData.FN_ENCHANT_WITH_LEVELS -> {
                    int level = this.nextIntInclusive(VaultLootData.FUNCTION_B[function],
                            VaultLootData.FUNCTION_C[function]);
                    VaultEnchantments.select(this.random, item, level, this.enchantScratch);
                }
                case VaultLootData.FN_ENCHANT_RANDOMLY -> {
                    int[] options = VaultLootData.ENCHANT_RANDOM_LISTS[VaultLootData.FUNCTION_A[function]];
                    int enchantment = options[this.random.nextInt(options.length)];
                    this.nextIntInclusive(1, VaultLootData.ENCHANTMENT_MAX_LEVEL[enchantment]);
                }
                default -> {
                }
            }
        }
    }

    private long enchantmentHash(int selected) {
        long hash = 0L;
        for (int i = 0; i < selected; i++) {
            hash += VaultComponentHash.enchantment(
                    this.enchantScratch.selectedEnchantment[i],
                    this.enchantScratch.selectedLevel[i]);
        }
        return hash;
    }

    /**
     * {@code Mth.nextInt} semantics: a constant range consumes no draw. This matters for
     * {@code enchant_randomly} on max-level-1 enchantments (multishot, mending, ...), where vanilla
     * does not call the random source at all.
     */
    private int nextIntInclusive(int min, int max) {
        return min >= max ? min : min + this.random.nextInt(max - min + 1);
    }
}
