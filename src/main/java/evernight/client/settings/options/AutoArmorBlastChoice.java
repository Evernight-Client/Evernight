package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoArmorBlastChoice implements NamedEntry {
    NONE("None"),
    LEGS("Legs"),
    SMART("1.21"),
    ALL("All");
    private final String displayName;

    AutoArmorBlastChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}