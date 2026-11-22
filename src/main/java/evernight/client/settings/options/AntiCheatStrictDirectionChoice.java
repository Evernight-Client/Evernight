package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AntiCheatStrictDirectionChoice implements NamedEntry {
    OFF("Off"),
    NCP("NCP"),
    GRIM("Grim");
    private final String displayName;

    AntiCheatStrictDirectionChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}