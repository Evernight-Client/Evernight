package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

public enum AuraSwapChoice implements NamedEntry {
    NORMAL("Normal"),
    SWAPBACK("SwapBack"),
    SILENT("Silent");
    private final String displayName;

    AuraSwapChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}