package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

/**
 * Enchantment {@code AutoTool} prefers when mining ore: silk touch keeps the ore block, fortune raises
 * the drops. The other enchantment is used as a fallback, then the plain fastest tool.
 */
public enum OreEnchantmentChoice implements NamedEntry {
    FORTUNE("Fortune"),
    SILK_TOUCH("SilkTouch");
    private final String displayName;

    OreEnchantmentChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
