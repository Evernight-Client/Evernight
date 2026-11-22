package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum SpeedMineValue121Choice implements NamedEntry {
    OFF("Off"),
    TICK("Tick"),
    SLOW("Slow");
    private final String displayName;

    SpeedMineValue121Choice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}