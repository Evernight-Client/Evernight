package evernight.addon;

import evernight.client.ClientContext;
import evernight.client.EvernightClient;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.modules.Module;
import evernight.client.settings.core.JsonConfigurable;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers and initialises Evernight addons.
 *
 * <p>Addons are ordinary Fabric mods that declare the {@value #ENTRYPOINT_KEY} entrypoint with a
 * subclass of {@link EvernightAddon}. Discovery happens once, at the end of the main client phase,
 * before configuration files are loaded. Each addon is isolated: an exception thrown from its
 * {@code onInitialize} is logged and recorded as {@link AddonInfo.Status#FAILED} without stopping the
 * client or the remaining addons.
 *
 * <p>The manager also owns the generated {@code ;addons} command and the directory used by
 * {@link AddonContext#dataDirectory()}.
 */
public final class AddonManager {
    /**
     * Entrypoint key an addon declares in its {@code fabric.mod.json}.
     */
    public static final String ENTRYPOINT_KEY = "evernight-addon";

    private static final AddonManager INSTANCE = new AddonManager();

    private final Map<String, AddonInfo> addons = new LinkedHashMap<>();
    private final Map<String, EvernightAddon> instances = new LinkedHashMap<>();
    private final Map<String, AddonContext> contexts = new LinkedHashMap<>();
    private final Map<Module, String> moduleOwners = new IdentityHashMap<>();
    private final List<JsonConfigurable> pendingConfigurables = new ArrayList<>();
    private boolean initialized;
    private boolean shutdown;

    private AddonManager() {
    }

    public static AddonManager getInstance() {
        return INSTANCE;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Loads every addon entrypoint. Idempotent; call once from {@code ClientInitializer}.
     */
    public void initialize() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        List<EntrypointContainer<EvernightAddon>> containers;
        try {
            containers = new ArrayList<>(
                    FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT_KEY, EvernightAddon.class));
            containers.sort(Comparator.comparing(container -> container.getProvider().getMetadata().getId()));
        } catch (Throwable failure) {
            EvernightClient.LOGGER.error("Could not discover '{}' addon entrypoints.", ENTRYPOINT_KEY, failure);
            return;
        }
        for (EntrypointContainer<EvernightAddon> container : containers) {
            this.loadAddon(container);
        }
        // The command only exists when it can list something; with no addons it stays hidden from ;help.
        if (!this.addons.isEmpty()) {
            CommandRegistry commandRegistry = ClientContext.commandRegistry;
            if (commandRegistry != null) {
                try {
                    commandRegistry.registerCommand(new AddonsCommand());
                } catch (RuntimeException failure) {
                    EvernightClient.LOGGER.warn("Could not register the ;addons command.", failure);
                }
            }
        }
        if (!this.addons.isEmpty()) {
            EvernightClient.LOGGER.info("Initialised {} Evernight addon(s).", this.addons.size());
        }
    }

    private void loadAddon(EntrypointContainer<EvernightAddon> container) {
        ModMetadata modMetadata = container.getProvider().getMetadata();
        String id = modMetadata.getId();
        if (EvernightClient.MOD_ID.equals(id)) {
            EvernightClient.LOGGER.warn("Ignoring '{}' addon entrypoint declared by Evernight itself.", ENTRYPOINT_KEY);
            return;
        }
        AddonMetadata metadata = toMetadata(modMetadata);
        AddonContext context = new AddonContext(this, metadata);
        this.contexts.put(id, context);
        try {
            EvernightAddon addon = container.getEntrypoint();
            context.loadLanguageFiles();
            addon.onInitialize(context);
            this.instances.put(id, addon);
            this.addons.put(id, new AddonInfo(metadata, AddonInfo.Status.LOADED, null,
                    context.moduleCount(), context.commandCount()));
            EvernightClient.LOGGER.info("Loaded addon {} {} ({})", metadata.displayName(), metadata.version(), id);
        } catch (Throwable failure) {
            EvernightClient.LOGGER.error("Addon {} failed to initialize.", id, failure);
            this.addons.put(id, new AddonInfo(metadata, AddonInfo.Status.FAILED, describe(failure),
                    context.moduleCount(), context.commandCount()));
        }
    }

    private static AddonMetadata toMetadata(ModMetadata modMetadata) {
        List<String> authors = modMetadata.getAuthors().stream().map(Person::getName).toList();
        String website = modMetadata.getContact().get("homepage")
                .or(() -> modMetadata.getContact().get("sources"))
                .orElse("");
        return new AddonMetadata(modMetadata.getId(), modMetadata.getName(),
                modMetadata.getVersion().getFriendlyString(), modMetadata.getDescription(), authors, website);
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + ": " + message;
    }

    /**
     * Loaded addons in deterministic (mod id) order, including addons that failed to initialise.
     */
    public List<AddonInfo> getAddons() {
        return List.copyOf(this.addons.values());
    }

    public Optional<AddonInfo> getAddon(String id) {
        return Optional.ofNullable(this.addons.get(id));
    }

    /**
     * Addon id that registered the given module, if it came from an addon rather than the client itself.
     */
    public Optional<String> getOwningAddon(Module module) {
        return Optional.ofNullable(this.moduleOwners.get(module));
    }

    /**
     * Configuration objects registered by addons before {@code ConfigurationManager} existed. The
     * initializer drains this list before loading configs so addon files are read from disk as well.
     */
    public List<JsonConfigurable> getPendingConfigurables() {
        return List.copyOf(this.pendingConfigurables);
    }

    void addPendingConfigurable(JsonConfigurable configurable) {
        this.pendingConfigurables.add(configurable);
    }

    void recordModule(String addonId, Module module) {
        this.moduleOwners.put(module, addonId);
    }

    /**
     * Calls {@link EvernightAddon#onShutdown()} in reverse load order and releases event-bus
     * subscriptions created through {@link AddonContext#subscribe(Object)}. Invoked from the client
     * shutdown hook after configuration is saved.
     */
    public void shutdown() {
        if (!this.initialized || this.shutdown) {
            return;
        }
        this.shutdown = true;
        List<EvernightAddon> loadOrder = new ArrayList<>(this.instances.values());
        Collections.reverse(loadOrder);
        for (EvernightAddon addon : loadOrder) {
            try {
                addon.onShutdown();
            } catch (Throwable failure) {
                EvernightClient.LOGGER.warn("Addon shutdown hook failed.", failure);
            }
        }
        for (AddonContext context : this.contexts.values()) {
            try {
                context.releaseSubscribers();
            } catch (Throwable failure) {
                EvernightClient.LOGGER.warn("Could not release addon event subscribers.", failure);
            }
        }
    }
}
