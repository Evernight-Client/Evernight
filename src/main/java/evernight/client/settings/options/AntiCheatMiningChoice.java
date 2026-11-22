package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AntiCheatMiningChoice implements NamedEntry {
    VANILLA("Vanilla"),
    GRIM("Grim"),
    GRIM_STRICT("GrimStrict");
    private final String displayName;

    AntiCheatMiningChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}