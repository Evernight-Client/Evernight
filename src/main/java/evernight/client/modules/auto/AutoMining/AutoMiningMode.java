package evernight.client.modules.auto.AutoMining;

import evernight.client.settings.core.NamedEntry;

/**
 * Target ore preset of the auto miner.
 *
 * <p>{@link #getName()} is the stable identifier used by configs and presets; the label shown in the
 * GUI is resolved from {@code names.values.*}. Each preset carries the default Y band the module
 * applies when the preset is selected.
 */
public enum AutoMiningMode implements NamedEntry {
    /**
     * Overworld diamonds. Diamond ore generates between Y -64 and 16 and is densest around Y -58.
     */
    DIAMOND("Diamond", -60, 16),
    /**
     * Nether ancient debris. Debris generates between Y 8 and 119 and is densest around Y 15; the
     * default band keeps the search on the dense lower band, players can widen it in the settings.
     */
    ANCIENT_DEBRIS("AncientDebris", 8, 22);

    private final String displayName;
    private final int defaultMinY;
    private final int defaultMaxY;

    AutoMiningMode(String displayName, int defaultMinY, int defaultMaxY) {
        this.displayName = displayName;
        this.defaultMinY = defaultMinY;
        this.defaultMaxY = defaultMaxY;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public int getDefaultMinY() {
        return this.defaultMinY;
    }

    public int getDefaultMaxY() {
        return this.defaultMaxY;
    }
}
