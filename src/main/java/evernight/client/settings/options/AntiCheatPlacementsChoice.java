package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AntiCheatPlacementsChoice implements NamedEntry {
    VANILLA("Vanilla"),
    PAPER("Paper");
    private final String displayName;

    AntiCheatPlacementsChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}