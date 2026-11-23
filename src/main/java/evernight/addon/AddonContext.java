package evernight.addon;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.commands.Command;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.LanguageTable;
import evernight.client.settings.core.JsonConfigurable;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registration facade handed to {@link EvernightAddon#onInitialize(AddonContext)}.
 *
 * <p>Everything an addon exposes flows through this object: modules (which Evernight serialises in
 * {@code modules.json}/{@code hud.json}), Brigadier commands, extra configuration files, event-bus
 * subscribers and language entries. The context also records ownership so {@link AddonManager} can
 * report what an addon registered and unsubscribe its listeners on shutdown.
 *
 * <p>Register from {@code onInitialize}: configuration files are read after addons initialise, so
 * settings registered at that point are restored from disk. Registering a module after the client
 * finished loading still works for the current session, but its saved state will not be applied.
 */
public final class AddonContext {
    /**
     * Language codes loaded from {@code assets/<addon id>/lang/<code>.json} by
     * {@link #loadLanguageFiles()}. Keep in sync with the client's language choices.
     */
    public static final List<String> SUPPORTED_LANGUAGES = List.of("en_us", "zh_cn");

    private final AddonManager manager;
    private final AddonMetadata metadata;
    private final Logger logger;
    private final List<Module> modules = new ArrayList<>();
    private final List<Command> commands = new ArrayList<>();
    private final List<Object> subscribers = new ArrayList<>();

    AddonContext(AddonManager manager, AddonMetadata metadata) {
        this.manager = manager;
        this.metadata = metadata;
        this.logger = LoggerFactory.getLogger("Evernight/Addon/" + metadata.id());
    }

    public AddonMetadata metadata() {
        return this.metadata;
    }

    public String id() {
        return this.metadata.id();
    }

    /**
     * Logger prefixed with the addon id; use it instead of {@code System.out} so addon problems show up
     * in the regular game log.
     */
    public Logger logger() {
        return this.logger;
    }

    public EventBus eventBus() {
        return ClientAccess.eventBus;
    }

    /**
     * Registers a module in the global {@link ModuleRegistry} and wires up its generated command
     * aliases. Duplicate module names are rejected with {@link IllegalStateException}; the manager
     * reports that as the addon's failure instead of silently dropping the module.
     *
     * <p>{@link HudWidgetModule} instances are stored in the same registry and persisted through
     * {@code hud.json}, so this method covers HUD widgets as well.
     */
    public <T extends Module> T registerModule(T module) {
        if (module == null) {
            throw new NullPointerException("module");
        }
        if (module instanceof AddonModule addonModule && !this.metadata.id().equals(addonModule.getAddonId())) {
            throw new IllegalArgumentException("Module " + module.getName() + " belongs to addon "
                    + addonModule.getAddonId() + ", not " + this.metadata.id());
        }
        ModuleRegistry registry = ClientContext.moduleRegistry;
        if (registry == null) {
            throw new IllegalStateException("Module registry is not initialised yet");
        }
        if (!registry.registerModule(module)) {
            throw new IllegalStateException("Module name is already registered: " + module.getName());
        }
        this.modules.add(module);
        this.manager.recordModule(this.metadata.id(), module);
        CommandRegistry commandRegistry = ClientContext.commandRegistry;
        if (commandRegistry != null) {
            commandRegistry.registerModuleAliasCommands(module);
        }
        return module;
    }

    /**
     * Convenience overload for HUD widgets; identical to {@link #registerModule(Module)}.
     */
    public <T extends HudWidgetModule> T registerHudModule(T module) {
        return this.registerModule(module);
    }

    /**
     * Registers a new module category owned by this addon and returns it for the
     * {@link AddonModule} constructor. Call this from {@code onInitialize} before registering the
     * modules that use the category, and only from {@code onInitialize}: the ClickGUI builds its
     * panels from {@link ModuleCategory#values()} afterwards.
     *
     * <p>The name is a stable English identifier that also appears in {@code ;drawn <category>} and
     * {@code ;preset ... specific <category>}. It must be non-blank and globally unique
     * (case-insensitively, built-in categories included); prefer alphanumerics and never rename it
     * after release. Duplicate or blank names throw {@link IllegalStateException}, which makes
     * {@link AddonManager} mark the addon as {@code FAILED}.
     *
     * <p>The display name is resolved on every call from
     * {@code addons.<addon id>.names.categories.<name>}. Add that key to both language files
     * ({@code en_us.json} and {@code zh_cn.json}); a missing key renders the full key so the gap is
     * visible instead of silently inventing an English label.
     */
    public ModuleCategory registerCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Module category name must not be blank");
        }
        String categoryName = name.trim();
        String translationKey = "addons." + this.metadata.id() + ".names.categories." + categoryName;
        return ModuleCategory.register(categoryName, translationKey);
    }

    /**
     * Registers a Brigadier command. Command names are the literal typed after the client prefix, so
     * keep them lower-case and unique.
     */
    public <T extends Command> T registerCommand(T command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        CommandRegistry registry = ClientContext.commandRegistry;
        if (registry == null) {
            throw new IllegalStateException("Command registry is not initialised yet");
        }
        if (!registry.registerCommand(command)) {
            throw new IllegalStateException("Command is already registered: " + command.getName());
        }
        this.commands.add(command);
        return command;
    }

    /**
     * Registers an additional JSON configuration file owned by the addon. The file name comes from
     * {@link JsonConfigurable#getConfigName()} and lives in the client's {@code evernight/} directory,
     * so addons should prefix it (for example {@code "myaddon.json"}).
     *
     * <p>Call this from {@code onInitialize}: Evernight loads configuration files right after addons
     * initialise. Registering later still persists the file on the next save, but the existing file is
     * not read back into the object.
     */
    public void registerConfig(JsonConfigurable configurable) {
        if (configurable == null) {
            throw new NullPointerException("configurable");
        }
        ConfigurationManager configuration = ClientContext.configurationManager;
        if (configuration != null) {
            configuration.registerConfigurable(configurable);
        } else {
            this.manager.addPendingConfigurable(configurable);
        }
    }

    /**
     * Subscribes an object with {@code @EventHandler} methods and remembers it so the manager can
     * unsubscribe on shutdown. Modules already subscribe themselves when enabled; use this for helper
     * objects only.
     */
    public void subscribe(Object subscriber) {
        ClientAccess.eventBus.subscribe(subscriber);
        this.subscribers.add(subscriber);
    }

    public void unsubscribe(Object subscriber) {
        ClientAccess.eventBus.unsubscribe(subscriber);
        this.subscribers.remove(subscriber);
    }

    /**
     * Parses a language JSON stream and merges it into the active language table. Overlay entries are
     * kept per language code and re-applied when the player switches languages.
     *
     * <p>The stream is read, not closed. Keys share the global namespace, so prefix addon-only text
     * with the addon id (for example {@code addons.myaddon.greeting}); module/setting display names use
     * the regular {@code names.modules.<Module>} / {@code names.settings.<Setting>} keys.
     */
    public void registerLanguage(String languageCode, InputStream input) {
        LanguageTable table = ClientContext.languageTable;
        if (table == null) {
            throw new IllegalStateException("Language table is not initialised yet");
        }
        try {
            table.registerOverlay(this.metadata.id(), languageCode, input);
        } catch (RuntimeException failure) {
            this.logger.warn("Ignoring invalid language file for {} ({})", this.metadata.id(), languageCode, failure);
        }
    }

    /**
     * Loads {@code assets/<addon id>/lang/en_us.json} and {@code assets/<addon id>/lang/zh_cn.json}
     * from the addon jar, if present. {@link AddonManager} calls this automatically before
     * {@link EvernightAddon#onInitialize(AddonContext)}, so addons normally do not need to call it.
     */
    public void loadLanguageFiles() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(this.metadata.id());
        if (container.isEmpty()) {
            this.logger.warn("Addon {} has no mod container; skipping language files", this.metadata.id());
            return;
        }
        for (String languageCode : SUPPORTED_LANGUAGES) {
            String resourcePath = "assets/" + this.metadata.id() + "/lang/" + languageCode + ".json";
            Optional<Path> languagePath = container.get().findPath(resourcePath);
            if (languagePath.isEmpty()) {
                continue;
            }
            try (InputStream input = Files.newInputStream(languagePath.get())) {
                this.registerLanguage(languageCode, input);
            } catch (IOException failure) {
                this.logger.warn("Could not read addon language file {}", resourcePath, failure);
            }
        }
    }

    /**
     * Addon-owned data directory under {@code evernight/addons/<addon id>}. Created on demand; use it for
     * files that are not part of the configuration system (caches, user lists, exports).
     */
    public Path dataDirectory() {
        Path directory = ConfigurationManager.ROOT_DIRECTORY.resolve("addons").resolve(this.metadata.id());
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            this.logger.warn("Could not create addon data directory {}", directory, failure);
        }
        return directory;
    }

    int moduleCount() {
        return this.modules.size();
    }

    int commandCount() {
        return this.commands.size();
    }

    void releaseSubscribers() {
        EventBus eventBus = ClientAccess.eventBus;
        for (Object subscriber : this.subscribers) {
            eventBus.unsubscribe(subscriber);
        }
        this.subscribers.clear();
    }
}
