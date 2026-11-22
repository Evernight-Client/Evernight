package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum TracersModeChoice implements NamedEntry {
    LINES("Lines"),
    MARKER("Marker");
    private final String displayName;

    TracersModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}