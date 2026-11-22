package evernight.client.modules;

import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;

public enum ModuleCategory implements NamedEntry {
    COMBAT("Combat"),
    MISC("Misc"),
    RENDER("Render"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    EXPLOIT("Exploit"),
    CLIENT("Client"),
    HUD("HUD");
    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Stable English identifier used by configs and command arguments.
     */
    @Override
    public String getName() {
        return this.displayName;
    }

    /**
     * Localised category title. Unlike {@link #getName()} this is resolved on every call, so a
     * language change is reflected without rebuilding the ClickGUI.
     */
    @Override
    public String getDisplayName() {
        return Translations.category(this.displayName);
    }
}