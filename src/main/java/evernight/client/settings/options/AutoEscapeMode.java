package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoEscapeMode implements NamedEntry {
    ALWAYS("Always"),
    COOLDOWN("Cooldown"),
    JOIN_ONLY("JoinOnly");

    private final String displayName;

    AutoEscapeMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
