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
 * <p>Lookups follow the client-wide fallback chain: the active language, then English, then the full
 * key. Java code never supplies display text, so addons must add every key to both the English and
 * Chinese language files. Addons that do not need localization may keep extending {@link Module}
 * directly and pass the addon id to {@link AddonContext#registerModule(Module)} as usual.
 */
public abstract class AddonModule extends Module {
    private final String addonId;

    /**
     * @param description retained for source compatibility; display text now always comes from
     *                    {@code addons.<id>.descriptions.modules.<Name>} and is never taken from Java
     *                    code. Add the key to both language files.
     */
    protected AddonModule(String addonId, String name, String description, ModuleCategory category,
                          String... aliases) {
        super(name, category, aliases);
        this.addonId = addonId;
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
        return Translations.get(this.translationKey("names.modules." + this.getName()));
    }

    @Override
    public String getDescription() {
        return Translations.get(this.translationKey("descriptions.modules." + this.getName()));
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
