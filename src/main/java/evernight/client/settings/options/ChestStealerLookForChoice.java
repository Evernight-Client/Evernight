package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum ChestStealerLookForChoice implements NamedEntry {
    NAME("Name"),
    ITEM("Item");
    private final String displayName;

    ChestStealerLookForChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}