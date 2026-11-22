package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum NoSlowStrictInventoryChoice implements NamedEntry {
    NONE("None"),
    NCP("NCP"),
    GRIM_STRICT("GrimStrict");
    private final String displayName;

    NoSlowStrictInventoryChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}