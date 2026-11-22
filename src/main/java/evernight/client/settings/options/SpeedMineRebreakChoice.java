package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum SpeedMineRebreakChoice implements NamedEntry {
    NONE("None"),
    FAST("Fast"),
    INSTANT("Instant");
    private final String name;

    SpeedMineRebreakChoice(String name) {
        this.name = name;
    }

    public boolean isNone() {
        return this == NONE;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public boolean isInstant() {
        return this == INSTANT;
    }

    public boolean isFast() {
        return this == FAST;
    }
}