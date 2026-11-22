package evernight.client.settings.options;

import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;

import java.util.Locale;

public enum DimensionType implements NamedEntry {
    OVERWORLD("overworld", "Overworld"),
    THE_NETHER("the_nether", "Nether"),
    THE_END("the_end", "End");
    private final String name;
    private final String displayName;

    DimensionType(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public boolean isEnd() {
        return this == THE_END;
    }

    public boolean isOverworld() {
        return this == OVERWORLD;
    }

    public boolean isNether() {
        return this == THE_NETHER;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return Translations.value(this.name);
    }

    /**
     * Stable storage key used by waypoints. It mirrors the pre-i18n {@code getDisplayName().toLowerCase()}
     * output so existing waypoint files keep resolving to the right dimension.
     */
    public String getWaypointKey() {
        return this.displayName.toLowerCase(Locale.ROOT);
    }

    /**
     * Untranslated English label used for data written to clipboard or files.
     */
    public String getEnglishDisplayName() {
        return this.displayName;
    }
}