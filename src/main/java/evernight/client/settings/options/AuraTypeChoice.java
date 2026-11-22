package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AuraTypeChoice implements NamedEntry {
    BOX("Box"),
    CIRCLE("Circle");
    private final String displayName;

    AuraTypeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}