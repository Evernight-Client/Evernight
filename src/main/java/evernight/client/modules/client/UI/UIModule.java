package evernight.client.modules.client.UI;

import com.mojang.blaze3d.platform.Window;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.EvernightClient;
import evernight.client.audio.BuiltinSound;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.gui.core.CursorType;
import evernight.client.gui.screens.clickgui.ClickGuiScreen;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.input.ClientKeybinds;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.Catppuccin;
import evernight.client.render.text.LanguageTable;
import evernight.client.utils.collections.NamedIdentifier;
import evernight.client.utils.world.Season;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.SearchMode;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.IdentifierSetting;
import evernight.client.settings.types.NumberSetting;

public final class UIModule extends Module {
    public static UIModule INSTANCE;
    public final Setting<StyleChoice> styleSetting;
    public final Setting<Float> animSpeedSetting;
    public final Setting<Float> scaleSetting;
    public final Setting<Boolean> snowSetting;
    public final Setting<Boolean> tilingSetting;
    public final Setting<SearchMode> searchSetting;
    public final Setting<Boolean> settingsSetting;
    public final Setting<Boolean> elementsSetting;
    public final Setting<Boolean> aeroSetting;
    public final Setting<Boolean> elementGradientSetting;
    public final Setting<Boolean> lineSetting;
    public final Setting<Boolean> bindsSetting;
    public final Setting<Boolean> gearSetting;
    public final Setting<Boolean> hintsSetting;
    public final Setting<Boolean> soundsSetting;
    public final Setting<Boolean> hoverSetting;
    public final Setting<NamedIdentifier> hoverSoundSetting;
    public final Setting<Float> hoverVolumeSetting;
    public final Setting<Boolean> leftClickSetting;
    public final Setting<NamedIdentifier> leftClickSoundSetting;
    public final Setting<Float> leftClickVolumeSetting;
    public final Setting<Boolean> rightClickSetting;
    public final Setting<NamedIdentifier> rightClickSoundSetting;
    public final Setting<Float> rightClickVolumeSetting;
    public final Setting<Boolean> textSetting;
    public final Setting<Color> textColorSetting;
    public final Setting<Color> enabledColorSetting;
    public final Setting<Color> colorSetting;
    public final Setting<Color> backgroundSetting;
    public final Setting<Color> buttonSetting;
    public final Setting<Color> enabledSetting;
    public final Setting<Color> tintSetting;
    public final Setting<Color> gradientColorSetting;
    final Setting<LanguageChoice> languageSetting;
    final Setting<Boolean> colorsSetting;
    private final AtomicBoolean panelResetPending = new AtomicBoolean(false);
    private final Setting<Boolean> resetSetting;

    public UIModule() {
        super("UI", ModuleCategory.CLIENT);
        this.resetSetting = this.add(new BooleanSetting(("Reset"), true));
        this.styleSetting = this.add(new EnumSetting<>(("Style"), UIModule.StyleChoice.PLAIN));
        this.animSpeedSetting = this.add(new NumberSetting<>("AnimSpeed", 1.0F, 0.5F, 2.5F));
        // The scale is editable both as a row and with Ctrl + wheel inside the ClickGUI.
        this.scaleSetting = this.add(new NumberSetting<>("Scale", 1.0F, 0.1F, 2.0F));
        this.snowSetting = this.add(new BooleanSetting("Snow", true));
        this.tilingSetting = this.add(new BooleanSetting("Tiling", true));
        this.languageSetting = this.add(new EnumSetting<>("Language", UIModule.LanguageChoice.ENGLISH));
        this.searchSetting = this.add(new EnumSetting<>("Search", SearchMode.AUTO));
        this.settingsSetting = this.add(new BooleanSetting("Settings", true));
        BooleanSetting elementsOption = new BooleanSetting("Elements", true);
        elementsOption.markAsGroup();
        elementsOption.markVisibilityDependency();
        this.elementsSetting = this.add(elementsOption);
        this.aeroSetting = this.add(new BooleanSetting("Aero", true)
                .dependsOn(this.elementsSetting));
        this.elementGradientSetting = this.add(new BooleanSetting("Gradient", true)
                .dependsOn(this.elementsSetting));
        Predicate<Object> lineVisibility = ignored -> this.elementsSetting.isExpanded();
        this.lineSetting = this.add(new BooleanSetting("Line", true, lineVisibility));
        Predicate<Object> bindsVisibility = ignored -> this.elementsSetting.isExpanded();
        this.bindsSetting = this.add(new BooleanSetting("Binds", true, bindsVisibility));
        Predicate<Object> gearVisibility = ignored -> this.elementsSetting.isExpanded();
        this.gearSetting = this.add(new BooleanSetting("Gear", true, gearVisibility));
        Predicate<Object> hintsVisibility = ignored -> this.elementsSetting.isExpanded();
        this.hintsSetting = this.add(new BooleanSetting("Hints", true, hintsVisibility));
        BooleanSetting soundsOption = new BooleanSetting("Sounds", true);
        soundsOption.markAsGroup();
        soundsOption.markVisibilityDependency();
        this.soundsSetting = this.add(soundsOption);
        Predicate<Object> hoverVisibility = ignored -> this.soundsSetting.isExpanded();
        BooleanSetting hoverOption = new BooleanSetting("Hover", true, hoverVisibility);
        hoverOption.markVisibilityDependency();
        this.hoverSetting = this.add(hoverOption);
        String hoverSoundKey = "Sound";
        this.hoverSoundSetting = this.add(new IdentifierSetting(hoverSoundKey, BuiltinSound.HOVER.getIdentifier())
                .dependsOn(this.soundsSetting, this.hoverSetting));
        NumberSetting<Float> hoverVolumeOption = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
        this.hoverVolumeSetting = this.add(hoverVolumeOption.dependsOn(this.soundsSetting, this.hoverSetting));
        Predicate<Object> leftClickVisibility = ignored -> this.soundsSetting.isExpanded();
        BooleanSetting leftClickOption = new BooleanSetting("LeftClick", true, leftClickVisibility);
        leftClickOption.markVisibilityDependency();
        this.leftClickSetting = this.add(leftClickOption);
        this.leftClickSoundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.CLICK.getIdentifier())
                .dependsOn(this.soundsSetting, this.leftClickSetting));
        NumberSetting<Float> leftClickVolumeOption = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
        this.leftClickVolumeSetting = this.add(leftClickVolumeOption.dependsOn(this.soundsSetting, this.leftClickSetting));
        Predicate<Object> rightClickVisibility = ignored -> this.soundsSetting.isExpanded();
        BooleanSetting rightClickOption = new BooleanSetting("RightClick", true, rightClickVisibility);
        rightClickOption.markVisibilityDependency();
        this.rightClickSetting = this.add(rightClickOption);
        this.rightClickSoundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.RCLICK.getIdentifier())
                .dependsOn(this.soundsSetting, this.rightClickSetting));
        NumberSetting<Float> rightClickVolumeOption = new NumberSetting<>("Volume", 0.5F, 0.0F, 1.0F);
        this.rightClickVolumeSetting = this.add(rightClickVolumeOption.dependsOn(this.soundsSetting, this.rightClickSetting));
        BooleanSetting textOption = new BooleanSetting("Text", false);
        textOption.markAsGroup();
        textOption.markVisibilityDependency();
        this.textSetting = this.add(textOption);
        String textColorKey = "TextColor";
        this.textColorSetting = this.add(new ColorSetting(textColorKey, Catppuccin.TEXT)
                .dependsOn(this.textSetting));
        this.enabledColorSetting = this.add(new ColorSetting("EnabledColor", Catppuccin.ROSEWATER)
                .dependsOn(this.textSetting));
        BooleanSetting colorsOption = new BooleanSetting("Colors", false);
        colorsOption.markAsGroup();
        colorsOption.markVisibilityDependency();
        this.colorsSetting = this.add(colorsOption);
        Predicate<Object> colorVisibility = ignored -> this.colorsSetting.isExpanded();
        this.colorSetting = this.add(new ColorSetting("Color", Catppuccin.withAlpha(Catppuccin.MAUVE, 130), colorVisibility));
        Predicate<Object> backgroundVisibility = ignored -> this.colorsSetting.isExpanded();
        this.backgroundSetting = this.add(new ColorSetting("Background", Catppuccin.withAlpha(Catppuccin.BASE, 60), backgroundVisibility));
        Predicate<Object> buttonVisibility = ignored -> this.colorsSetting.isExpanded();
        this.buttonSetting = this.add(new ColorSetting("Button", Catppuccin.withAlpha(Catppuccin.SURFACE1, 115), buttonVisibility));
        Predicate<Object> enabledVisibility = ignored -> this.colorsSetting.isExpanded();
        this.enabledSetting = this.add(new ColorSetting("Enabled", Catppuccin.withAlpha(Catppuccin.MAUVE, 90), enabledVisibility));
        Predicate<Object> tintVisibility = ignored -> this.colorsSetting.isExpanded();
        this.tintSetting = this.add(new ColorSetting("Tint", Catppuccin.withAlpha(Catppuccin.CRUST, 130), tintVisibility));
        Predicate<Object> gradientColorVisibility = ignored -> this.colorsSetting.isExpanded();
        this.gradientColorSetting = this.add(new ColorSetting("Gradient", Catppuccin.withAlpha(Catppuccin.MAUVE, 18), gradientColorVisibility));
        Class<UIModule> eventOwner = UIModule.class;
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribeStatic(eventOwner);
        this.ignoreState();
        this.setDrawn(false);
        INSTANCE = this;
        Setting<Boolean> resetCallbackSetting = this.resetSetting;
        Runnable resetChangeCallback = () -> {
            Setting<Boolean> reset = this.resetSetting;
            if (!(reset.getValue())) {
                this.resetSetting.setValue(true);
                if (ClientContext.clickGui != null) {
                    ClickGuiScreen screen = ClientContext.getClickGui();
                    screen.resetGuiLayout();
                }
            }
        };
        resetCallbackSetting.setChangeCallback(resetChangeCallback);
        Setting<StyleChoice> styleCallbackSetting = this.styleSetting;
        Runnable styleChangeCallback = () -> this.panelResetPending.set(true);
        styleCallbackSetting.setChangeCallback(styleChangeCallback);
        Setting<LanguageChoice> languageCallbackSetting = this.languageSetting;
        Runnable languageChangeCallback = () -> {
            LanguageTable tableCapture = ClientContext.languageTable;
            Setting<LanguageChoice> language = this.languageSetting;
            UIModule.LanguageChoice selectedLanguage = language.getValue();
            InputStream languageStream = selectedLanguage.openStream();
            tableCapture.load(languageStream, selectedLanguage.getLanguageCode());
        };
        languageCallbackSetting.setChangeCallback(languageChangeCallback);
        this.hoverSoundSetting.setConfigName("HoverSound");
        this.hoverVolumeSetting.setConfigName("HoverVolume");
        this.leftClickSoundSetting.setConfigName("LeftClickSound");
        this.leftClickVolumeSetting.setConfigName("LeftClickVolume");
        this.rightClickSoundSetting.setConfigName("RightClickSound");
        this.rightClickVolumeSetting.setConfigName("RightClickVolume");
        this.backgroundSetting.setConfigName("BgColor");
        this.buttonSetting.setConfigName("BgButton");
        this.enabledSetting.setConfigName("BgEnabled");
        this.gradientColorSetting.setConfigName("GradientColor");
        Season winterCapture = Season.WINTER;
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        if (!winterCapture.containsMonth(month)) {
            this.snowSetting.setValue(false);
            this.unregister(this.snowSetting);
        }
    }

    @EventHandler
    private static void onCursorTick(MinecraftTickEvent event) {
        if (!(ClientAccess.evernightMinecraft.screen instanceof ClientScreen)) {
            CursorType cursor = CursorType.STANDARD;
            cursor.apply();
        }
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable() || ClientAccess.evernightMinecraft.screen instanceof ClickGuiScreen) {
            return;
        }
        if (ClientAccess.evernightMinecraft.screen instanceof ChatScreen) {
            TaskScheduler guiScheduler = ClientContext.taskScheduler;
            Runnable guiOpenTask = () -> {
                ClickGuiScreen deferredScreen = ClientContext.getClickGui();
                deferredScreen.open();
            };
            guiScheduler.scheduleRotationTask(guiOpenTask, 0);
        } else {
            ClickGuiScreen screen = ClientContext.getClickGui();
            screen.open();
        }
    }

    /**
     * The ClickGUI toggle is bound to the vanilla {@link net.minecraft.client.KeyMapping} that
     * {@link ClientKeybinds} registers, so "Options &gt; Controls &gt; Key Binds" is the only place
     * that owns the key. The client model is projected from it on every read, which keeps the
     * ClickGUI row, the config file and the vanilla screen in sync.
     */
    @Override
    public Keybind getKeybind() {
        return ClientKeybinds.getClickGuiKeybind().withMode(Keybind.Mode.TOGGLE);
    }

    @Override
    public void modifyKeybind(Function modifier) {
        Keybind updated = (Keybind) modifier.apply(this.getKeybind());
        ClientKeybinds.setClickGuiKeybind(updated);
    }

    @Override
    protected boolean usesExternalKeybind() {
        return true;
    }

    @Override
    public void onDisable() {
        if (ClientAccess.evernightMinecraft.screen instanceof ClickGuiScreen) {
            ClientContext.getClickGui().closeWithTransition();
        }
    }

    @EventHandler
    private void onMinecraftTick(MinecraftTickEvent event) {
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        Window window = minecraft.getWindow();
        long windowHandle = window.handle();
        if (GLFW.glfwGetMouseButton(windowHandle, 0) == 1) {
            return;
        }
        AtomicBoolean pendingReset = this.panelResetPending;
        if (!pendingReset.get()) {
            return;
        }
        ClickGuiScreen screen = ClientContext.getClickGui();
        screen.resetGuiLayout();
        this.resetSetting.setValue(true);
        this.panelResetPending.set(false);
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerTickEvent event) {
        if (!(ClientAccess.evernightMinecraft.screen instanceof ClientScreen)) {
            this.toggle();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundDisconnectPacket) {
            this.disable();
        }
    }

    public float getAnimationSpeed() {
        return this.animSpeedSetting.getValue();
    }

    public enum LanguageChoice implements NamedEntry {
        ENGLISH("English", "en_us"),
        CHINESE("Chinese", "zh_cn");
        private final String displayName;
        private final String languageCode;

        LanguageChoice(String displayName, String languageCode) {
            this.displayName = displayName;
            this.languageCode = languageCode;
        }

        public InputStream openStream() {
            String languageCode = this.languageCode;
            return EvernightClient.class.getResourceAsStream("/assets/evernight/lang/%s.json".formatted(languageCode));
        }

        public String getLanguageCode() {
            return this.languageCode;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    public enum StyleChoice implements NamedEntry {
        PLAIN("Plain"),
        SLIM("Slim");
        private final String displayName;

        StyleChoice(String displayName) {
            this.displayName = displayName;
        }

        public int getHeightPadding() {
            return this == SLIM ? 3 : 4;
        }

        public int getWidthPadding() {
            return this == SLIM ? 4 : 7;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}