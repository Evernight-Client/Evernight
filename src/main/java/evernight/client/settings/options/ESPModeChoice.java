package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum ESPModeChoice implements NamedEntry {
    PLAIN("Plain"),
    TEXT("Text"),
    BOTH("Both");
    private final String displayName;

    ESPModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}