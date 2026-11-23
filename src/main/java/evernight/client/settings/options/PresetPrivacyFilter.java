package evernight.client.settings.options;

import com.google.gson.JsonObject;
import evernight.client.modules.Module;

import java.util.Set;

/**
 * Modules whose settings must never be written into exported presets.
 *
 * <p>{@code ExtraScreenshot} stores an Imgur {@code ClientId} that may be a personal client ID or a
 * {@code Bearer} access token, so exporting it would hand a credential to whoever receives the
 * preset. The module is still exported by the explicit single-module clipboard preset: that path is
 * an intentional, user-chosen action and is documented as such.
 */
public final class PresetPrivacyFilter {
    public static final PresetPrivacyFilter INSTANCE = new PresetPrivacyFilter();
    private static final Set<String> EXCLUDED_MODULES = Set.of("ExtraScreenshot");

    private PresetPrivacyFilter() {
    }

    public boolean isExcluded(Module module) {
        return module != null && EXCLUDED_MODULES.contains(module.getConfigName());
    }

    /**
     * Removes every excluded module from a module-keyed preset object before it is written.
     *
     * @return whether anything was removed, so callers can rewrite an already-existing preset file
     */
    public boolean removeExcludedModules(JsonObject preset) {
        if (preset == null) {
            return false;
        }
        boolean changed = false;
        for (String moduleName : EXCLUDED_MODULES) {
            if (preset.has(moduleName)) {
                preset.remove(moduleName);
                changed = true;
            }
        }
        return changed;
    }
}
