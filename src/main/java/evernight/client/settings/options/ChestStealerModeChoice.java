package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum ChestStealerModeChoice implements NamedEntry {
    STEAL("Steal"),
    FILL("Fill"),
    DROP("Drop"),
    REFILL("Refill"),
    REKIT("Rekit");
    private final String displayName;

    ChestStealerModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}