package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum MultiTaskModeChoice implements NamedEntry {
    NONE("None"),
    STRICT("Strict"),
    OFFHAND("Offhand"),
    ALL("Always");
    private final String displayName;

    MultiTaskModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}