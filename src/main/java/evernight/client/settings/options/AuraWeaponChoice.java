package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AuraWeaponChoice implements NamedEntry {
    NONE("None"),
    REQUIRE("Require"),
    SWAP("Swap");
    private final String displayName;

    AuraWeaponChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}