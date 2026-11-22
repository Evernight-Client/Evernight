package evernight.client.settings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientContext;
import evernight.client.config.legacy.LegacyBranding;
import evernight.client.managers.registry.AbstractRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.render.LogoutSpots.LogoutSpotsModule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

public final class ModulePresetFilter {
    public static final ModulePresetFilter INSTANCE = new ModulePresetFilter();

    private ModulePresetFilter() {
    }

    public void retainScalarSettings(JsonObject modulesJson) {
        Iterator moduleValues = modulesJson.asMap().values().iterator();
        while (moduleValues.hasNext()) {
            JsonElement moduleElement = (JsonElement) moduleValues.next();
            if (moduleElement instanceof JsonObject moduleJson) {
                moduleJson.remove("key");
                if (moduleJson.has("settings")) {
                    JsonElement settingsElement = moduleJson.get("settings");
                    if (settingsElement.isJsonObject()) {
                        ArrayList nestedSettingKeys = new ArrayList();
                        JsonObject settingsJson = settingsElement.getAsJsonObject();
                        Iterator settingEntries = settingsJson.asMap().entrySet().iterator();
                        while (settingEntries.hasNext()) {
                            Entry settingEntry = (Entry) settingEntries.next();
                            JsonElement settingValue = (JsonElement) settingEntry.getValue();
                            if (settingValue.isJsonObject()) {
                                nestedSettingKeys.add(settingEntry.getKey());
                            }
                        }
                        settingEntries = nestedSettingKeys.iterator();
                        while (settingEntries.hasNext()) {
                            String removalKey = (String) settingEntries.next();
                            JsonObject removalSettingsJson = settingsElement.getAsJsonObject();
                            removalSettingsJson.remove(removalKey);
                        }
                    }
                }
            }
        }
    }

    public void retainModuleSettings(JsonObject modulesJson, Predicate includedModules) {
        Iterator moduleNames = modulesJson.asMap().keySet().iterator();
        while (moduleNames.hasNext()) {
            String moduleName = (String) moduleNames.next();
            if (modulesJson.get(moduleName) instanceof JsonObject moduleJson) {
                AbstractRegistry modules = ClientContext.moduleRegistry;
                Predicate<Module> modulePredicate = module -> {
                    return module.getConfigName().equals(LegacyBranding.canonicalModuleName(moduleName));
                };
                Optional matchingModule = modules.findFirst(modulePredicate);
                if (!matchingModule.isEmpty()) {
                    boolean includeModule = includedModules.test(matchingModule.get());
                    if (!includeModule || matchingModule.orElse(null) instanceof LogoutSpotsModule) {
                        moduleJson.remove("enabled");
                    }
                    moduleJson.remove("key");
                    if (moduleJson.has("settings")) {
                        JsonElement settingsElement = moduleJson.get("settings");
                        if (settingsElement.isJsonObject()) {
                            ArrayList filteredSettingKeys = new ArrayList();
                            JsonObject settingsJson = settingsElement.getAsJsonObject();
                            Iterator settingEntries = settingsJson.asMap().entrySet().iterator();
                            while (settingEntries.hasNext()) {
                                Entry settingEntry = (Entry) settingEntries.next();
                                if (includeModule) {
                                    break;
                                }
                                String settingName = (String) settingEntry.getKey();
                                JsonElement settingValue = (JsonElement) settingEntry.getValue();
                                if (!this.isVisualSetting(settingName, settingValue)) {
                                    filteredSettingKeys.add(settingEntry.getKey());
                                }
                            }
                            settingEntries = filteredSettingKeys.iterator();
                            while (settingEntries.hasNext()) {
                                String removalKey = (String) settingEntries.next();
                                JsonObject removalSettingsJson = settingsElement.getAsJsonObject();
                                removalSettingsJson.remove(removalKey);
                            }
                        }
                    }
                }
            }
        }
    }

    public void retainKeyBindings(JsonObject modulesJson) {
        Iterator moduleValues = modulesJson.asMap().values().iterator();
        while (moduleValues.hasNext()) {
            JsonElement moduleElement = (JsonElement) moduleValues.next();
            if (moduleElement instanceof JsonObject moduleJson) {
                moduleJson.remove("enabled");
                moduleJson.remove("settings");
            }
        }
    }

    public boolean isVisualSetting(String settingName, JsonElement settingValue) {
        return settingName.equalsIgnoreCase("render") || settingName.equalsIgnoreCase("linewidth") || settingValue.isJsonObject();
    }
}