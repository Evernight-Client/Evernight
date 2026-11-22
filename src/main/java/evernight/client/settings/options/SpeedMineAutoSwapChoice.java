package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum SpeedMineAutoSwapChoice implements NamedEntry {
    NONE("None"),
    NORMAL("Normal"),
    SILENT("Silent");
    private final String displayName;

    SpeedMineAutoSwapChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}