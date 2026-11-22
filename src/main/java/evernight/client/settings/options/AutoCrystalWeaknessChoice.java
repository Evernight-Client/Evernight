package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoCrystalWeaknessChoice implements NamedEntry {
    NONE("None"),
    NORMAL("Normal"),
    SILENT("Silent");
    private final String displayName;

    AutoCrystalWeaknessChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}