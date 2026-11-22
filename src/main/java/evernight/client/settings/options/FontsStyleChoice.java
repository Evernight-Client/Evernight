package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum FontsStyleChoice implements NamedEntry {
    PLAIN("Plain"),
    BOLD("Bold"),
    ITALIC("Italic"),
    BOLDITALIC("BoldItalic");
    private final String displayName;

    FontsStyleChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}