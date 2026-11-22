package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoCrystalSequentialChoice implements NamedEntry {
    NONE("None"),
    STRICT("Strict"),
    STRONG("Strong");
    private final String displayName;

    AutoCrystalSequentialChoice(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return this != NONE;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}