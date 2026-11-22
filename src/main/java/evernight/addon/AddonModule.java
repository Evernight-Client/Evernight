package evernight.addon;

import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;

/**
 * Module base class for addons. It resolves display text from the addon's own language namespace
 * ({@code addons.<id>.*}) instead of the client namespace, so addon language files never need
 * {@code evernight.}-prefixed keys and cannot collide with the client or another addon.
 *
 * <p>Looked up keys:
 * <ul>
 *     <li>{@code addons.<id>.names.modules.<ModuleName>} - display name</li>
 *     <li>{@code addons.<id>.descriptions.modules.<ModuleName>} - description</li>
 *     <li>{@code addons.<id>.names.settings.<SettingName>} - setting labels, applied automatically by
 *     {@link #add(Setting)}</li>
 * </ul>
 *
 * <p>Missing keys fall back to the English module/setting identifier, so a partially translated addon
 * still renders readably. Addons that do not need localization may keep extending {@link Module}
 * directly and pass the addon id to {@link AddonContext#registerModule(Module)} as usual.
 */
public abstract class AddonModule extends Module {
    private final String addonId;
    private final String fallbackDescription;

    protected AddonModule(String addonId, String name, String description, ModuleCategory category,
                          String... aliases) {
        super(name, category, aliases);
        this.addonId = addonId;
        this.fallbackDescription = description;
    }

    public String getAddonId() {
        return this.addonId;
    }

    /**
     * Builds a full key in this addon's namespace, for example {@code translationKey("ui.greeting")}.
     */
    public String translationKey(String key) {
        return "addons." + this.addonId + "." + key;
    }

    @Override
    public String getDisplayName() {
        String localized = Translations.find(this.translationKey("names.modules." + this.getName()));
        return localized != null ? localized : this.getName();
    }

    @Override
    public String getDescription() {
        String localized = Translations.find(this.translationKey("descriptions.modules." + this.getName()));
        return localized != null ? localized : this.fallbackDescription;
    }

    @Override
    public <S extends Setting<?>> S add(S setting) {
        setting.setDisplayNameKey(this.translationKey("names.settings." + setting.getName()));
        return super.add(setting);
    }

    @Override
    public <S extends Setting<?>> S add(S setting, Setting<?> precedingSetting) {
        setting.setDisplayNameKey(this.translationKey("names.settings." + setting.getName()));
        return super.add(setting, precedingSetting);
    }
}
