package evernight.client;

import evernight.client.gui.screens.clickgui.ClickGuiScreen;
import evernight.client.gui.screens.hud.HudEditorScreen;
import evernight.client.integrations.baritone.BaritoneBridge;
import evernight.client.integrations.xaero.WaypointAdapter;
import evernight.client.integrations.xaero.WaypointSync;
import evernight.client.modules.Module;
import evernight.client.render.esp.FeatureBoxRenderer;
import evernight.client.render.text.LanguageTable;
import evernight.client.utils.core.FrameTiming;
import evernight.client.utils.combat.TotemPopTracker;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.movement.MovementSpeedTracker;
import evernight.client.utils.world.BlockBreakTracker;
import evernight.client.utils.world.MapDataCache;

import java.util.Objects;
import evernight.client.managers.audio.SoundManager;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.config.ScreenshotCredentialStore;
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
import evernight.client.managers.registry.ShaderRegistry;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.managers.registry.StashRegistry;
import evernight.client.managers.registry.TradeRegistry;
import evernight.client.managers.registry.WaypointRegistry;
import evernight.client.managers.social.WebhookManager;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.managers.world.HoleManager;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.managers.world.WorldStateManager;

public final class ClientContext {
    public static TickRateController tickRateController;
    public static ConfigurationManager configurationManager;
    public static RotationManager rotationManager;
    public static volatile ModuleRegistry moduleRegistry;
    public static FakeplayerManager fakeplayerManager;
    public static CommandRegistry commandRegistry;
    public static FrameTiming frameTiming;
    public static ServerStateManager serverStateManager;
    public static SocialRegistry socialRegistry;
    public static WaypointRegistry waypointRegistry;
    public static HoleManager holeManager;
    public static SoundManager soundManager;
    public static MacroRegistry macroRegistry;
    public static InventoryStateManager inventoryStateManager;
    public static MovementPredictionManager movementPredictionManager;
    public static TotemPopTracker totemPopTracker;
    public static MovementSpeedTracker movementSpeedTracker;
    public static PlayerStateTracker playerStateTracker;
    public static FeatureBoxRenderer featureBoxRenderer;
    public static TickRateController.PlayerTicks playerTickController;
    public static StashRegistry stashRegistry;
    public static TaskScheduler taskScheduler;
    public static HudModuleRegistry hudModuleRegistry;
    public static WebhookManager webhookManager;
    public static ScreenshotCredentialStore screenshotCredentialStore;
    public static WorldStateManager worldStateManager;
    public static CrystalInteractionManager crystalInteractionManager;
    public static MapDataCache mapDataCache;
    public static KitRegistry kitRegistry;
    public static AliasRegistry aliasRegistry;
    public static ChatFilterRegistry chatFilterRegistry;
    public static GlintRegistry glintRegistry;
    public static BlockBreakTracker blockBreakTracker;
    public static ShaderRegistry shaderRegistry;
    public static TradeRegistry tradeRegistry;
    public static BlockColorRegistry blockColorRegistry;
    public static AutoPresetRegistry autoPresetRegistry;
    public static InputOverrideManager inputOverrides;
    public static LanguageTable languageTable;
    /** True while {@code ConfigurationManager.load()} is reading the config files. */
    public static boolean configurationLoading;
    public static SwingInteractionManager swingManager;
    public static BaritoneBridge baritoneBridge;
    public static WaypointAdapter waypointAdapter;
    public static WaypointSync waypointSync;
    public static ClickGuiScreen clickGui;
    private static HudEditorScreen hudEditor;

    private ClientContext() {
    }

    public static <T extends Module> T getModule(Class<T> moduleType) {
        Objects.requireNonNull(moduleType, "Module type");
        ModuleRegistry registry = moduleRegistry;
        return registry == null ? null : registry.getModule(moduleType);
    }

    public static ClickGuiScreen getClickGui() {
        if (clickGui == null) {
            clickGui = new ClickGuiScreen();
        }
        return clickGui;
    }

    public static HudEditorScreen getHudEditor() {
        if (hudEditor == null) {
            hudEditor = new HudEditorScreen();
        }
        return hudEditor;
    }
}