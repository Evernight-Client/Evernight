package evernight.client.modules.client.Baritone;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PermanentModule;
import evernight.client.modules.movement.SafeWalk.SafeWalkModule;
import evernight.client.modules.movement.Step.StepModule;
import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.modules.player.AutoTool.AutoToolModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.core.Setting;

import java.awt.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class BaritoneModule extends PermanentModule {
    private static StepModule step;
    private static AutoToolModule autoTool;
    private static SafeWalkModule safeWalk;

    static {
        step = ClientContext.getModule(StepModule.class);
        autoTool = ClientContext.getModule(AutoToolModule.class);
        safeWalk = ClientContext.getModule(SafeWalkModule.class);
    }

    private final Setting<Boolean> freeLookSetting;
    private final Setting<Boolean> backFillSetting;
    private final Setting<Boolean> logOnArrivalSetting;
    private final Setting<Boolean> assumeEnabledSetting;
    private final Setting<Boolean> replaceColorsSetting;
    private final Setting<Color> colorSetting;
    private final Setting<Color> goalSetting;
    private List<AssumedSetting> assumedSettings;

    public BaritoneModule() {
        super("Baritone", ModuleCategory.CLIENT);
        this.requiresMod("baritone", "baritone-meteor");
        this.freeLookSetting = this.add(new BooleanSetting(("FreeLook"), true));
        this.backFillSetting = this.add(new BooleanSetting("BackFill", false));
        this.logOnArrivalSetting = this.add(new BooleanSetting("LogOnArrival", false));
        this.assumeEnabledSetting = this.add(new BooleanSetting("AssumeEnabled", false));
        BooleanSetting replaceColorsSetting = new BooleanSetting("ReplaceColors", true);
        replaceColorsSetting.markVisibilityDependency();
        this.replaceColorsSetting = this.add(replaceColorsSetting);
        this.colorSetting = this.add(new ColorSetting(("Color"), new Color(-4351489, true)).dependsOn(this.replaceColorsSetting));
        this.goalSetting = this.add(new ColorSetting("Goal", new Color(-4351489, true)).dependsOn(this.replaceColorsSetting));
        if (ClientContext.baritoneBridge.isAvailable()) {
            Settings.Setting chatControlSetting = BaritoneAPI.getSettings().chatControl;
            chatControlSetting.value = false;
        }
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    public static void bindModuleDependencies(ModuleRegistry registry) {
        step = registry.getModule(StepModule.class);
        autoTool = registry.getModule(AutoToolModule.class);
        safeWalk = registry.getModule(SafeWalkModule.class);
    }

    private List<AssumedSetting> getAssumedSettings() {
        if (this.assumedSettings == null) {
            this.assumedSettings = AssumedSetting.create(step, autoTool, safeWalk);
        }
        return this.assumedSettings;
    }

    @EventHandler
    private void onMinecraftTick(MinecraftTickEvent event) {
        if (!ClientContext.baritoneBridge.isAvailable()) {
            return;
        }
        Settings settings = BaritoneAPI.getSettings();
        Settings.Setting targetSetting = settings.disconnectOnArrival;
        targetSetting.value = this.logOnArrivalSetting.getValue();
        targetSetting = settings.backfill;
        Setting<Boolean> backFillSetting = this.backFillSetting;
        targetSetting.value = backFillSetting.getValue();
        targetSetting = settings.freeLook;
        Setting<Boolean> freeLookSetting = this.freeLookSetting;
        targetSetting.value = freeLookSetting.getValue();
        boolean censorCoordinates = CoordinateHiderModule.shouldCensorBaritone();
        settings.censorCoordinates.value = censorCoordinates;
        settings.censorRanCommands.value = censorCoordinates;
        Setting<Boolean> replaceColorsSetting = this.replaceColorsSetting;
        Boolean replaceColors = replaceColorsSetting.getValue();
        if (replaceColors) {
            Setting<Color> pathColorSetting = this.colorSetting;
            Color configuredPathColor = pathColorSetting.getValue();
            Color pathColor = ColorUtils.withAlpha(configuredPathColor, 255);
            Setting<Color> goalColorSetting = this.goalSetting;
            Color configuredGoalColor = goalColorSetting.getValue();
            Color goalColor = ColorUtils.withAlpha(configuredGoalColor, 255);
            settings.colorBestPathSoFar.value = pathColor;
            settings.colorGoalBox.value = goalColor;
            settings.colorInvertedGoalBox.value = goalColor;
            settings.colorCurrentPath.value = pathColor;
            settings.colorMostRecentConsidered.value = pathColor;
            settings.colorBlocksToBreak.value = pathColor;
            settings.colorBlocksToPlace.value = pathColor;
            settings.colorBlocksToWalkInto.value = pathColor;
            settings.colorNextPath.value = pathColor;
            targetSetting = settings.pathRenderLineWidthPixels;
            targetSetting.value = (1.5F);
            targetSetting = settings.goalRenderLineWidthPixels;
            targetSetting.value = (1.5F);
        }
        Setting<Boolean> assumeEnabledSetting = this.assumeEnabledSetting;
        Boolean assumeEnabled = assumeEnabledSetting.getValue();
        if (assumeEnabled) {
            this.getAssumedSettings().forEach(AssumedSetting::sync);
        }
    }

    private static class AssumedSetting {
        private final Function<Settings, Settings.Setting<Boolean>> settingSelector;
        private final Supplier<Boolean> moduleEnabled;
        private boolean lastEnabledValue;

        private AssumedSetting(Function<Settings, Settings.Setting<Boolean>> settingSelector, Supplier<Boolean> moduleEnabled) {
            this.settingSelector = settingSelector;
            this.moduleEnabled = moduleEnabled;
            this.lastEnabledValue = this.getSetting().value;
        }

        private static List<AssumedSetting> create(StepModule step, AutoToolModule autoTool, SafeWalkModule safeWalk) {
            return List.of(new AssumedSetting(settings -> settings.assumeStep, step::isToggled),
                    new AssumedSetting(settings -> settings.assumeExternalAutoTool, autoTool::isToggled),
                    new AssumedSetting(settings -> settings.assumeSafeWalk, safeWalk::isToggled));
        }

        public void sync() {
            boolean enabled = this.moduleEnabled.get();
            if (enabled) {
                this.getSetting().value = true;
                this.lastEnabledValue = true;
            } else if (this.lastEnabledValue) {
                this.getSetting().value = false;
                this.lastEnabledValue = false;
            }
        }

        private Settings.Setting<Boolean> getSetting() {
            return this.settingSelector.apply(BaritoneAPI.getSettings());
        }
    }
}