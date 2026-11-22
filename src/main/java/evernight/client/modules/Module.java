package evernight.client.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.NamedFeature;
import evernight.client.events.ui.ModuleToggleEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.registry.AliasRegistry;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.Toggleable;
import evernight.client.utils.inventory.RegistryContainer;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.function.Function;
import evernight.client.config.legacy.LegacyBranding;

/**
 * Base class for every toggleable client feature.
 *
 * <p>A module owns an ordered list of {@link Setting}s (exposed as a
 * {@code RegistryContainer<Setting<?>>} so GUI code can enumerate them), a keybind and the enabled
 * state. {@link #enable()} subscribes the instance to the {@link EventBus} and posts a
 * {@link ModuleToggleEvent}; {@link #disable()} reverses that. Behaviour lives in
 * {@link EventHandler} methods, with {@link #onEnable()}, {@link #onDisable()} and {@link #onToggle()}
 * as lifecycle hooks.
 */
public class Module extends NamedFeature implements Toggleable, ClientAccess, RegistryContainer<Setting<?>>, JsonConfigurable {
    private final ModuleCategory category;
    private final List<Setting<?>> settings;
    private final String[] aliases;
    private Keybind keybind;
    private boolean enabled;
    private boolean drawn;
    private boolean ignoreEnabledState;

    public Module(String name, ModuleCategory category, String... aliases) {
        super(name);
        this.settings = new ArrayList<>();
        this.keybind = Keybind.UNBOUND;
        this.drawn = true;
        this.category = category;
        String[] suppliedAliases = Objects.requireNonNullElseGet(aliases, () -> new String[0]);
        this.aliases = Arrays.copyOf(suppliedAliases, suppliedAliases.length + 1);
        this.aliases[suppliedAliases.length] = name;
    }

    public <S extends Setting<?>> S add(S setting) {
        this.register(setting);
        return setting;
    }

    public <S extends Setting<?>> S add(S setting, Setting<?> precedingSetting) {
        int insertionIndex = this.settings.indexOf(precedingSetting) + 1;
        this.settings.add(insertionIndex, setting);
        return setting;
    }

    @Override
    public boolean register(Setting<?> setting) {
        return this.settings.add(setting);
    }

    @Override
    public void enable() {
        if (this.isToggled()) {
            return;
        }
        this.enabled = true;
        ClientAccess.eventBus.subscribe(this);
        EventBus eventBus = ClientAccess.eventBus;
        ModuleToggleEvent toggleEvent = new ModuleToggleEvent(this);
        eventBus.post(toggleEvent);
        this.onToggle();
        this.onEnable();
    }

    @Override
    public boolean unregister(Setting<?> setting) {
        return this.settings.remove(setting);
    }

    public String[] getAliases() {
        return this.aliases;
    }

    public String getInfo() {
        return null;
    }

    @Override
    public boolean isToggled() {
        return this.enabled;
    }

    public void onEnable() {
    }

    public void onToggle() {
    }

    public boolean isDrawn() {
        return this.drawn;
    }

    public void setDrawn(boolean drawn) {
        this.drawn = drawn;
    }

    public Keybind getKeybind() {
        return this.keybind;
    }

    public void setKeybind(Keybind keybind) {
        this.keybind = keybind;
    }

    public void onDisable() {
    }

    public String getDescription() {
        return Translations.moduleDescription(this.getName());
    }

    /**
     * Localized name used purely for display. {@link #getName()} and {@link #getConfigName()} keep the
     * English identifier so commands, aliases and config files are unaffected by the language.
     */
    public String getDisplayName() {
        return Translations.module(this.getName());
    }

    @Override
    public void fromJson(JsonElement json) {
        json = evernight.client.config.legacy.LegacyBranding.migrateModule(getName(), json);
        if (!(json instanceof JsonObject moduleJson)) {
            return;
        }
        if (moduleJson.has("settings") && moduleJson.get("settings") instanceof JsonObject settingsJson) {
            Iterator<Setting<?>> settingsIterator = this.getSettings().iterator();
            while (settingsIterator.hasNext()) {
                Setting<?> setting = settingsIterator.next();
                if (setting.isExcludedFromConfig() || !settingsJson.has(setting.getConfigName())) {
                    continue;
                }
                try {
                    setting.fromJson(settingsJson.get(setting.getConfigName()));
                } catch (Exception invalidSetting) {
                }
            }
        }
        if (moduleJson.has("drawn")) {
            this.setDrawn(moduleJson.get("drawn").getAsBoolean());
        }
        if (moduleJson.has("key") && !this.usesExternalKeybind()) {
            Function<Keybind, Keybind> keybindModifier = keybind -> {
                int keyCode = moduleJson.get("key").getAsInt();
                Keybind codedKeybind = keybind.withCode(keyCode);
                Keybind.Mode mode = moduleJson.has("state")
                        ? Keybind.Mode.fromSerializedName(moduleJson.get("state").getAsString())
                        : Keybind.Mode.TOGGLE;
                return codedKeybind.withMode(mode);
            };
            this.modifyKeybind(keybindModifier);
        }
        if (moduleJson.has("mouse") && !this.usesExternalKeybind()) {
            Function<Keybind, Keybind> mouseModifier = keybind -> keybind.withMouse(moduleJson.get("mouse").getAsBoolean());
            this.modifyKeybind(mouseModifier);
        }
        if (moduleJson.has("enabled") && !this.ignoreEnabledState) {
            this.setToggled(moduleJson.get("enabled").getAsBoolean());
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject moduleJson = new JsonObject();
        if (!this.ignoreEnabledState) {
            moduleJson.addProperty("enabled", this.enabled);
        }
        Keybind serializedKeybind = this.getKeybind();
        moduleJson.addProperty("key", serializedKeybind.code());
        moduleJson.addProperty("state", serializedKeybind.mode().getSerializedName());
        moduleJson.addProperty("mouse", serializedKeybind.mouse());
        moduleJson.addProperty("drawn", this.drawn);
        JsonObject settingsJson = new JsonObject();
        Iterator<Setting<?>> settingsIterator = this.getSettings().iterator();
        while (settingsIterator.hasNext()) {
            Setting<?> setting = settingsIterator.next();
            if (!setting.isGroupOnly() && !setting.isExcludedFromConfig()) {
                String configName = setting.getConfigName();
                JsonElement settingJson = setting.toJson();
                settingsJson.add(configName, settingJson);
            }
        }
        moduleJson.add("settings", settingsJson);
        return moduleJson;
    }

    public ModuleCategory getCategory() {
        return this.category;
    }

    @Override
    public void disable() {
        if (!this.isToggled()) {
            return;
        }
        ClientAccess.eventBus.unsubscribe(this);
        this.enabled = false;
        EventBus eventBus = ClientAccess.eventBus;
        ModuleToggleEvent toggleEvent = new ModuleToggleEvent(this);
        eventBus.post(toggleEvent);
        this.onToggle();
        this.onDisable();
    }

    public String getInfoString() {
        AliasRegistry aliasRegistry = ClientContext.aliasRegistry;
        String moduleAlias = AliasRegistry.getModuleAlias(aliasRegistry, this);
        String infoSuffix;
        if (this.getInfo() != null) {
            String openingColor = String.valueOf(ChatFormatting.GRAY);
            String infoColor = String.valueOf(ChatFormatting.WHITE);
            String info = this.getInfo();
            String closingColor = String.valueOf(ChatFormatting.GRAY);
            infoSuffix = openingColor + " [" + infoColor + info + closingColor + "]";
        } else {
            infoSuffix = "";
        }
        return moduleAlias + infoSuffix;
    }

    public void modifyKeybind(Function modifier) {
        this.keybind = (Keybind) modifier.apply(this.keybind);
    }

    /**
     * Whether the module's key lives outside of the client config, e.g. inside a vanilla
     * {@link net.minecraft.client.KeyMapping}. Such modules must not let the config file
     * overwrite the binding the player configured in the vanilla controls screen.
     */
    protected boolean usesExternalKeybind() {
        return false;
    }

    @Override
    public String getConfigName() {
        return this.getName();
    }

    protected void ignoreState() {
        this.ignoreEnabledState = true;
    }

    public List<Setting<?>> getSettings() {
        return this.settings;
    }

    @Override
    public Collection<Setting<?>> getRegistry() {
        return this.settings;
    }
}