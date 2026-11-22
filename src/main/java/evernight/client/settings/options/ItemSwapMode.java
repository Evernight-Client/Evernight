package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum ItemSwapMode implements NamedEntry {
    NONE("None"),
    NORMAL("Normal"),
    SILENT("Silent");
    private final String displayName;

    ItemSwapMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}