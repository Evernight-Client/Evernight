package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoCrystalSilentChoice implements NamedEntry {
    NORMAL("Normal"),
    ALTERNATIVE("Alternative");
    private final String displayName;

    AutoCrystalSilentChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}