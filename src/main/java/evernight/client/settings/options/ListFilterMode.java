package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;

import java.util.Collection;
import java.util.Objects;

public enum ListFilterMode implements NamedEntry {
    ANY("Any"),
    WHITELIST("WhiteList"),
    BLACKLIST("BlackList");
    private final String displayName;

    ListFilterMode(String displayName) {
        this.displayName = displayName;
    }

    public static boolean matches(ListFilterMode mode, Object value, Setting<?> setting) {
        Objects.requireNonNull(mode);
        Setting<?> collectionSetting = setting;
        return matches(mode, value, (Collection) collectionSetting.getValue());
    }

    public static boolean matches(ListFilterMode mode, Object value, Collection entries) {
        return switch (mode) {
            case ANY -> true;
            case WHITELIST -> entries.contains(value);
            case BLACKLIST -> !entries.contains(value);
        };
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public boolean isFiltering() {
        return !this.isAny();
    }

    public boolean isAny() {
        return this == ANY;
    }
}