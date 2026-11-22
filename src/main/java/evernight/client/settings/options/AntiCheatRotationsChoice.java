package evernight.client.settings.options;

import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;

public enum AntiCheatRotationsChoice implements NamedEntry {
    MOTION("Motion"),
    SILENT("Silent");
    private final String displayName;

    AntiCheatRotationsChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    @Override
    public String getDisplayName() {
        if (this == MOTION) {
            return Translations.value("AntiCheatMotion");
        }
        return NamedEntry.super.getDisplayName();
    }
}
