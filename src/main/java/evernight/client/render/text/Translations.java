package evernight.client.render.text;

import evernight.client.ClientContext;

import java.util.Locale;

/**
 * Convenience accessors for the {@code lang/*.json} translation keys used by the client UI.
 *
 * <p>Every client-owned key lives under the {@link #PREFIX} namespace in {@code lang/*.json}; the
 * vanilla {@code key.evernight.click_gui} entry is resolved by Minecraft itself. Lookups for foreign
 * namespaces (for example an addon's {@code addons.<id>.*} keys) use {@link #get(String)} directly.
 *
 * <p>The key layout follows the original Evernight/Open Zio scheme after the prefix:
 * <ul>
 *     <li>{@code evernight.descriptions.modules.<module>} - module tooltip</li>
 *     <li>{@code evernight.descriptions.settings.<module>.<setting>} - per-module setting tooltip</li>
 *     <li>{@code evernight.descriptions.settings.<setting>} - shared setting tooltip fallback</li>
 *     <li>{@code evernight.names.modules.<module>} - module label shown in the ClickGUI and HUD</li>
 *     <li>{@code evernight.names.settings.<setting>} - setting label shown in the ClickGUI</li>
 *     <li>{@code evernight.names.values.<display name>} - enum option label</li>
 *     <li>{@code evernight.names.categories.<category>} - module category title</li>
 *     <li>{@code evernight.names.sounds.<sound>} - bundled sound label</li>
 *     <li>{@code evernight.ui.<key>} - hard-coded interface strings</li>
 * </ul>
 *
 * <p>Every lookup runs against the live table, so switching languages updates the UI without a
 * restart. English text is not duplicated in Java: missing keys fall back to the key itself (or to
 * the stable identifier for module, setting and enum lookups).
 */
public final class Translations {
    /**
     * Namespace reserved for every client-owned key in {@code lang/*.json}. Vanilla keys
     * ({@code key.*}, {@code category.*}) keep their original form because Minecraft resolves them
     * directly from the same resource pack.
     */
    public static final String PREFIX = "evernight.";

    private Translations() {
    }

    private static LanguageTable table() {
        return ClientContext.languageTable;
    }

    private static String getOrDefault(String key, String fallback) {
        LanguageTable languageTable = table();
        return languageTable == null ? fallback : languageTable.getTranslationOrDefault(key, fallback);
    }

    /**
     * Looks up a full key without any fallback, returning {@code null} when it is missing. Used by
     * addon-namespaced display names so they can fall back to their English identifier instead of
     * leaking the key into the UI.
     */
    public static String find(String key) {
        LanguageTable languageTable = table();
        return languageTable == null ? null : languageTable.getTranslation(key);
    }

    public static String get(String key) {
        return getOrDefault(key, key);
    }

    public static String ui(String key) {
        return get(PREFIX + "ui." + key);
    }

    /**
     * Looks up {@code ui.<key>} and applies {@link String#format(String, Object...)} so sentences with
     * embedded values can be translated as a whole instead of being concatenated from fragments.
     * Returns the raw pattern when formatting fails.
     */
    public static String format(String key, Object... args) {
        String pattern = ui(key);
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (RuntimeException ignoredFailure) {
            return pattern;
        }
    }

    /**
     * Display-only module name. Command and config identifiers always keep using
     * {@link evernight.client.modules.Module#getName()}.
     */
    public static String module(String name) {
        return getOrDefault(PREFIX + "names.modules." + name, name);
    }

    public static String setting(String name) {
        return getOrDefault(PREFIX + "names.settings." + name, name);
    }

    public static String value(String name) {
        return getOrDefault(PREFIX + "names.values." + name, name);
    }

    public static String category(String name) {
        return getOrDefault(PREFIX + "names.categories." + name, name);
    }

    public static String panel(String name) {
        return getOrDefault(PREFIX + "names.panels." + name, name);
    }

    /**
     * Display-only sound name. Sound files and config keys keep using the raw identifier, so user
     * sound packs that have no translation simply fall back to their file name.
     */
    public static String sound(String name) {
        return getOrDefault(PREFIX + "names.sounds." + name, name);
    }

    public static String moduleDescription(String moduleName) {
        return getOrDefault(PREFIX + "descriptions.modules." + moduleName, "");
    }

    /**
     * Looks up a setting tooltip, preferring the module-specific key and falling back to a shared
     * key that only contains the setting name. Returns {@code null} when neither key exists.
     */
    public static String settingDescription(String moduleName, String settingName) {
        String specific = getOrDefault(PREFIX + "descriptions.settings." + moduleName + "." + settingName, null);
        if (specific != null) {
            return specific;
        }
        return getOrDefault(PREFIX + "descriptions.settings." + settingName, null);
    }
}
