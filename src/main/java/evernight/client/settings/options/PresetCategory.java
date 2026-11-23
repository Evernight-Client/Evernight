package evernight.client.settings.options;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientContext;
import evernight.client.config.json.JsonUtils;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.MacroRegistry;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.*;
import evernight.client.modules.misc.KillEffects.KillEffectsModule;
import evernight.client.modules.render.LogoutSpots.LogoutSpotsModule;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.modules.client.Fonts.FontsModule;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.modules.client.Notifications.NotificationsModule;
import evernight.client.modules.client.UI.UIModule;

public enum PresetCategory implements JsonConfigurable, NamedEntry {
    MODULES("Modules") {
        @Override
        public void fromJson(JsonElement json) {
            ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
            JsonObject object = json.getAsJsonObject();
            filter.retainScalarSettings(object);
            PresetPrivacyFilter.INSTANCE.removeExcludedModules(object);
            super.fromJson(json);
        }

        @Override
        public JsonElement toJson() {
            JsonObject preset = new JsonObject();
            this.writeMetadata(preset);
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            Iterator<?> modules = ((List<?>) moduleRegistry.getRegistry()).iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!PresetPrivacyFilter.INSTANCE.isExcluded(module)
                        && !(module instanceof HudWidgetModule) && !(module instanceof FontsModule)) {
                    JsonObject moduleJson = new JsonObject();
                    moduleJson.addProperty("enabled", module.isToggled());
                    JsonObject settingsJson = new JsonObject();
                    Iterator<?> settings = module.getSettings().iterator();
                    while (settings.hasNext()) {
                        Setting<?> setting = (Setting<?>) settings.next();
                        if (!(setting instanceof ColorSetting)) {
                            if (!setting.isGroupOnly()) {
                                if (!setting.isExcludedFromConfig()) {
                                    String settingName = setting.getConfigName();
                                    JsonElement settingJson = setting.toJson();
                                    settingsJson.add(settingName, settingJson);
                                }
                            }
                        }
                    }
                    if (!JsonUtils.isRecursivelyEmpty(settingsJson)) {
                        moduleJson.add("settings", settingsJson);
                    }
                    String moduleName = module.getConfigName();
                    preset.add(moduleName, moduleJson);
                }
            }
            return preset;
        }
    },
    BINDS("Binds") {
        @Override
        public void fromJson(JsonElement json) {
            ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
            JsonObject object = json.getAsJsonObject();
            filter.retainKeyBindings(object);
            super.fromJson(json);
        }

        @Override
        public JsonElement toJson() {
            JsonObject preset = new JsonObject();
            this.writeMetadata(preset);
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            Iterator<?> modules = ((List<?>) moduleRegistry.getRegistry()).iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!(module instanceof HudWidgetModule)) {
                    JsonObject moduleJson = new JsonObject();
                    Keybind keybind = module.getKeybind();
                    moduleJson.addProperty("key", keybind.code());
                    keybind = module.getKeybind();
                    Keybind.Mode keyMode = keybind.mode();
                    String modeName = keyMode.name();
                    String modeValue = modeName.toLowerCase();
                    moduleJson.addProperty("state", modeValue);
                    keybind = module.getKeybind();
                    moduleJson.addProperty("mouse", keybind.mouse());
                    String moduleName = module.getConfigName();
                    preset.add(moduleName, moduleJson);
                }
            }
            return preset;
        }
    },
    HUD("Hud") {
        @Override
        public void fromJson(JsonElement json) {
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            Iterator<?> modules = ((List<?>) moduleRegistry.getRegistry()).iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!PresetPrivacyFilter.INSTANCE.isExcluded(module) && this.isHudModule(module)) {
                    try {
                        JsonObject preset = json.getAsJsonObject();
                        String moduleName = module.getConfigName();
                        JsonElement moduleElement = preset.get(moduleName);
                        JsonObject moduleJson = moduleElement.getAsJsonObject();
                        String[] bindingKeys = {"key", "state", "mouse"};
                        JsonUtils.removeProperties(moduleJson, bindingKeys);
                        module.fromJson(moduleJson);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        @Override
        public JsonElement toJson() {
            JsonObject preset = new JsonObject();
            this.writeMetadata(preset);
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            Iterator<?> modules = ((List<?>) moduleRegistry.getRegistry()).iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!PresetPrivacyFilter.INSTANCE.isExcluded(module) && this.isHudModule(module)) {
                    JsonElement moduleElement = module.toJson();
                    JsonObject moduleJson = moduleElement.getAsJsonObject();
                    String[] bindingKeys = {"key", "state", "mouse"};
                    JsonUtils.removeProperties(moduleJson, bindingKeys);
                    String moduleName = module.getConfigName();
                    preset.add(moduleName, moduleJson);
                }
            }
            return preset;
        }

        private boolean isHudModule(Module module) {
            return module instanceof HudWidgetModule || module instanceof HUDModule;
        }
    },
    COLORS("Colors") {
        @Override
        public void fromJson(JsonElement json) {
            ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
            JsonObject object = json.getAsJsonObject();
            Predicate<Module> includeModule = module -> false;
            filter.retainModuleSettings(object, includeModule);
            PresetPrivacyFilter.INSTANCE.removeExcludedModules(object);
            super.fromJson(json);
        }

        @Override
        public JsonElement toJson() {
            JsonObject preset = new JsonObject();
            this.writeMetadata(preset);
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            Iterator<?> modules = ((List<?>) moduleRegistry.getRegistry()).iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!PresetPrivacyFilter.INSTANCE.isExcluded(module)
                        && !(module instanceof HudWidgetModule)) {
                    JsonObject moduleJson = new JsonObject();
                    JsonObject settingsJson = new JsonObject();
                    Iterator<?> settings = module.getSettings().iterator();
                    while (settings.hasNext()) {
                        Setting<?> setting = (Setting<?>) settings.next();
                        if (setting instanceof ColorSetting) {
                            if (!setting.isExcludedFromConfig()) {
                                String settingName = setting.getConfigName();
                                JsonElement settingJson = setting.toJson();
                                settingsJson.add(settingName, settingJson);
                            }
                        }
                    }
                    moduleJson.add("settings", settingsJson);
                    if (!JsonUtils.isRecursivelyEmpty(moduleJson)) {
                        String moduleName = module.getConfigName();
                        preset.add(moduleName, moduleJson);
                    }
                }
            }
            return preset;
        }
    },
    VISUALS("Visuals") {
        private static final Set<Class<?>> VISUAL_MODULE_CLASSES;
        static {
            Class<KillEffectsModule> killEffectsClass = KillEffectsModule.class;
            Class<NotificationsModule> notificationsClass = NotificationsModule.class;
            Class<ColorsModule> colorsClass = ColorsModule.class;
            Class<HUDModule> hudClass = HUDModule.class;
            Class<FontsModule> fontsClass = FontsModule.class;
            Class<UIModule> uiClass = UIModule.class;
            VISUAL_MODULE_CLASSES = new HashSet<>(
                    List.of(uiClass, fontsClass, hudClass, colorsClass, notificationsClass, killEffectsClass)
            );
        }

        @Override
        public void fromJson(JsonElement json) {
            ModulePresetFilter filter = ModulePresetFilter.INSTANCE;
            JsonObject object = json.getAsJsonObject();
            Predicate<Module> includeModule = this::isVisualModule;
            filter.retainModuleSettings(object, includeModule);
            PresetPrivacyFilter.INSTANCE.removeExcludedModules(object);
            super.fromJson(json);
        }

        @Override
        public JsonElement toJson() {
            JsonObject preset = new JsonObject();
            this.writeMetadata(preset);
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            Iterator<?> modules = ((List<?>) moduleRegistry.getRegistry()).iterator();
            while (modules.hasNext()) {
                Module module = (Module) modules.next();
                if (!PresetPrivacyFilter.INSTANCE.isExcluded(module)
                        && !(module instanceof HudWidgetModule)) {
                    JsonObject moduleJson = new JsonObject();
                    boolean visualModule = this.isVisualModule(module);
                    boolean logoutSpots = module instanceof LogoutSpotsModule;
                    if (visualModule && !logoutSpots) {
                        moduleJson.addProperty("enabled", module.isToggled());
                    }
                    JsonObject settingsJson = new JsonObject();
                    Iterator<?> settings = module.getSettings().iterator();
                    while (settings.hasNext()) {
                        Setting<?> setting = (Setting<?>) settings.next();
                        if (!setting.isExcludedFromConfig()) {
                            if (this.isVisualSetting(setting) || visualModule) {
                                String settingName = setting.getConfigName();
                                JsonElement settingJson = setting.toJson();
                                settingsJson.add(settingName, settingJson);
                            }
                        }
                    }
                    moduleJson.add("settings", settingsJson);
                    if (!JsonUtils.isRecursivelyEmpty(moduleJson)) {
                        String moduleName = module.getConfigName();
                        preset.add(moduleName, moduleJson);
                    }
                }
            }
            return preset;
        }

        boolean isVisualModule(Module module) {
            if (module instanceof HudWidgetModule) {
                return true;
            }
            if (VISUAL_MODULE_CLASSES.contains(module.getClass())) {
                return true;
            }
            if (module.getCategory() != ModuleCategory.RENDER) {
                return module.getCategory() == ModuleCategory.HUD;
            }
            return true;
        }

        boolean isVisualSetting(Setting<?> setting) {
            String settingName = setting.getName();
            if (settingName.equalsIgnoreCase("render")) {
                return true;
            }
            settingName = setting.getName();
            if (settingName.equalsIgnoreCase("linewidth")) {
                return true;
            }
            settingName = setting.getName();
            if (!settingName.equalsIgnoreCase("colormode")) {
                settingName = setting.getName();
                if (!settingName.equalsIgnoreCase("rendermode")) {
                    return setting instanceof ColorSetting;
                }
            }
            return true;
        }
    },
    MACRO("Macro") {
        @Override
        public void fromJson(JsonElement json) {
            MacroRegistry registryForLock = ClientContext.macroRegistry;
            synchronized (registryForLock.getRegistry()) {
                MacroRegistry registryForClear = ClientContext.macroRegistry;
                registryForClear.getRegistry().clear();
                MacroRegistry registryForLoad = ClientContext.macroRegistry;
                registryForLoad.fromJson(json);
            }
        }

        @Override
        public JsonElement toJson() {
            MacroRegistry macroRegistry = ClientContext.macroRegistry;
            return macroRegistry.toJson();
        }
    },
    ALL("All") {
        @Override
        public void fromJson(JsonElement json) {
            JsonObject object = json.getAsJsonObject();
            PresetPrivacyFilter.INSTANCE.removeExcludedModules(object);
            super.fromJson(json);
        }

        @Override
        public JsonElement toJson() {
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            JsonElement modulesJson = moduleRegistry.toJson();
            JsonObject preset = modulesJson.getAsJsonObject();
            PresetPrivacyFilter.INSTANCE.removeExcludedModules(preset);
            return preset;
        }
    };
    private final String displayName;
    private final Path directory;

    PresetCategory(String displayName) {
        this.displayName = displayName;
        Path presetsDirectory = ConfigurationManager.PRESETS_DIRECTORY;
        this.directory = presetsDirectory.resolve(displayName.toLowerCase());
    }

    public static PresetCategory findByName(String name) {
        for (PresetCategory category : values()) {
            String categoryName = category.getName();
            if (categoryName.equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }

    public Path getDirectory() {
        return this.directory;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    @Override
    public void fromJson(JsonElement json) {
        ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
        moduleRegistry.fromJson(json);
    }

    public void writeMetadata(JsonObject json) {
        json.addProperty("client", "evernight");
        String categoryName = this.getName();
        Locale locale = Locale.ROOT;
        String categoryValue = categoryName.toLowerCase(locale);
        json.addProperty("category", categoryValue);
    }
}