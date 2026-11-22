package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoCrystalInstantChoice implements NamedEntry {
    ALWAYS("Always"),
    STRICT("Strict"),
    NONE("None");
    private final String displayName;

    AutoCrystalInstantChoice(String displayName) {
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