package evernight.client.bootstrap;

import evernight.addon.AddonManager;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.EvernightClient;
import evernight.client.events.core.ClientInitializationEvent;
import evernight.client.input.ClientKeybinds;
import evernight.client.integrations.baritone.BaritoneAdapter;
import evernight.client.integrations.baritone.BaritoneBridge;
import evernight.client.integrations.baritone.DisabledBaritoneBridge;
import evernight.client.integrations.xaero.DisabledWaypointAdapter;
import evernight.client.integrations.xaero.XaeroWaypointAdapter;
import evernight.client.integrations.xaero.XaeroWaypointSync;
import evernight.client.render.esp.FeatureBoxRenderer;
import evernight.client.render.font.FontSupport;
import evernight.client.render.text.LanguageTable;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.utils.core.FrameTiming;
import evernight.client.utils.combat.TotemPopTracker;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.inventory.CustomItemRegistries;
import evernight.client.utils.movement.MovementSpeedTracker;
import evernight.client.utils.world.BlockBreakTracker;
import evernight.client.utils.world.MapDataCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Locale;
import java.util.Locale.Category;
import evernight.client.managers.audio.SoundManager;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.player.CrystalInteractionManager;
import evernight.client.managers.player.FakeplayerManager;
import evernight.client.managers.player.InputOverrideManager;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.managers.player.MovementPredictionManager;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.managers.player.TickRateController;
import evernight.client.managers.registry.AliasRegistry;
import evernight.client.managers.registry.AutoPresetRegistry;
import evernight.client.managers.registry.BlockColorRegistry;
import evernight.client.managers.registry.ChatFilterRegistry;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.managers.registry.GlintRegistry;
import evernight.client.managers.registry.HudModuleRegistry;
import evernight.client.managers.registry.KitRegistry;
import evernight.client.managers.registry.MacroRegistry;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.managers.registry.StashRegistry;
import evernight.client.managers.registry.TradeRegistry;
import evernight.client.managers.registry.WaypointRegistry;
import evernight.client.managers.social.WebhookManager;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.managers.world.HoleManager;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.managers.world.WorldStateManager;

public final class ClientInitializer {
    private boolean managersInitialized;
    private boolean clientInitialized;

    private static void requireEnvironment() {
        FabricLoader fabric = FabricLoader.getInstance();
        if (fabric.getEnvironmentType() != EnvType.CLIENT || Runtime.version().feature() < 21) {
            throw new IllegalStateException("Evernight requires the Fabric client environment and Java 21 or newer.");
        }
        String minecraft = fabric.getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("Minecraft mod container is missing."))
                .getMetadata().getVersion().getFriendlyString();
        if (!"1.21.11".equals(minecraft)) {
            throw new IllegalStateException("Evernight requires Minecraft 1.21.11; found " + minecraft);
        }
    }

    public void initialize() {
        requireEnvironment();
        if (this.managersInitialized) {
            return;
        }
        // Constructors publish services in dependency order before modules bind to them.
        ClientContext.baritoneBridge = BaritoneBridge.isBaritoneLoaded()
                ? new BaritoneAdapter() : new DisabledBaritoneBridge();
        ClientContext.waypointAdapter = FabricLoader.getInstance().isModLoaded("xaerominimap")
                ? new XaeroWaypointAdapter() : new DisabledWaypointAdapter();
        FontSupport.initialize();
        ClientContext.languageTable = new LanguageTable();
        ClientContext.inputOverrides = new InputOverrideManager();
        ClientContext.blockColorRegistry = new BlockColorRegistry();
        ClientContext.playerStateTracker = new PlayerStateTracker();
        ClientContext.serverStateManager = new ServerStateManager();
        ClientContext.blockBreakTracker = new BlockBreakTracker();
        ClientContext.tickRateController = new TickRateController();
        ClientContext.playerTickController = new TickRateController.PlayerTicks();
        ClientContext.movementPredictionManager = new MovementPredictionManager();
        ClientContext.holeManager = new HoleManager();
        ClientContext.totemPopTracker = new TotemPopTracker();
        ClientContext.movementSpeedTracker = new MovementSpeedTracker();
        ClientContext.rotationManager = new RotationManager();
        ClientContext.waypointRegistry = new WaypointRegistry();
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
            ClientContext.waypointSync = new XaeroWaypointSync();
        }
        ClientContext.featureBoxRenderer = new FeatureBoxRenderer();
        ClientContext.taskScheduler = new TaskScheduler();
        ClientContext.moduleRegistry = new ModuleRegistry();
        ClientContext.hudModuleRegistry = new HudModuleRegistry();
        ClientContext.tradeRegistry = new TradeRegistry();
        ClientContext.fakeplayerManager = new FakeplayerManager();
        ClientContext.commandRegistry = new CommandRegistry();
        ClientContext.frameTiming = new FrameTiming();
        ClientContext.macroRegistry = new MacroRegistry();
        ClientContext.socialRegistry = new SocialRegistry();
        ClientContext.soundManager = new SoundManager();
        ClientContext.inventoryStateManager = new InventoryStateManager();
        ClientContext.stashRegistry = new StashRegistry();
        ClientContext.webhookManager = new WebhookManager();
        ClientContext.worldStateManager = new WorldStateManager();
        ClientContext.crystalInteractionManager = new CrystalInteractionManager();
        ClientContext.mapDataCache = new MapDataCache();
        ClientContext.kitRegistry = new KitRegistry();
        ClientContext.aliasRegistry = new AliasRegistry();
        ClientContext.chatFilterRegistry = new ChatFilterRegistry();
        ClientContext.glintRegistry = new GlintRegistry();
        ClientContext.autoPresetRegistry = new AutoPresetRegistry();
        ClientContext.swingManager = new SwingInteractionManager();
        AddonManager.getInstance().initialize();
        Locale.setDefault(Category.FORMAT, Locale.US);
        this.managersInitialized = true;
        EvernightClient.LOGGER.info("Evernight main initialization completed.");
    }

    public void initializeClient() {
        requireEnvironment();
        if (!this.managersInitialized) {
            throw new IllegalStateException("Evernight client initialization requires the main phase.");
        }
        if (this.clientInitialized) {
            return;
        }
        ModuleDependencies.bind(ClientContext.moduleRegistry);
        // Register before Options is built so Fabric can attach the binding to the vanilla
        // key mapping list that "Options > Controls > Key Binds" displays.
        ClientKeybinds.register();
        ClientAccess.eventBus.post(new ClientInitializationEvent());
        ClientContext.soundManager.reload();
        ClientContext.configurationManager = new ConfigurationManager();
        ConfigurationManager configuration = ClientContext.configurationManager;
        for (JsonConfigurable configurable : AddonManager.getInstance().getPendingConfigurables()) {
            configuration.registerConfigurable(configurable);
        }
        CustomItemRegistries.initialize();
        configuration.load();
        this.clientInitialized = true;
        EvernightClient.LOGGER.info("Evernight client initialization completed.");
    }
}