package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum WarpRechargeChoice implements NamedEntry {
    HOLD("Hold"),
    INSTANT("Instant"),
    NONE("None");
    private final String displayName;

    WarpRechargeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}