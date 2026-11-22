package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum NoSlowModeChoice implements NamedEntry {
    NONE("None"),
    NCP("NCP"),
    GRIMOLD("GrimOld"),
    GRIMV3("Slow"),
    _2B2T("2b2t"),
    CONSTANTIAM("Constantiam");
    private final String displayName;

    NoSlowModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}