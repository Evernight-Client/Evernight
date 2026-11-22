package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum BlockerModeChoice implements NamedEntry {
    DAMAGE("Damage"),
    DESTROY("Destroy"),
    INSTANT("Instant");
    private final String displayName;

    BlockerModeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}