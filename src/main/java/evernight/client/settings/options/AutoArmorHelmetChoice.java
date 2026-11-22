package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AutoArmorHelmetChoice implements NamedEntry {
    BEST("Best"),
    TURTLE("Turtle"),
    GOLD("Golden"),
    NONE("None");
    private final String displayName;

    AutoArmorHelmetChoice(String displayName) {
        this.displayName = displayName;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isTurtle() {
        return this == TURTLE;
    }

    public boolean isGold() {
        return this == GOLD;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}