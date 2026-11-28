package evernight.client.modules.client.HUD;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.gui.screens.clickgui.ClickGuiScreen;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.gui.screens.hud.HudEditorScreen;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.render.NoRender.NoRenderModule;
import evernight.client.render.color.Catppuccin;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.hud.HudWidgetRenderer;
import evernight.client.utils.core.InterpolatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class HUDModule extends Module {
    private static NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);
    public final Setting<EffectIconMode> iconsSetting;
    public final Setting<Float> textScaleSetting;
    public final Setting<Boolean> smoothWidthSetting;
    public final Setting<Boolean> parallaxSetting;
    public final Setting<Boolean> bloomSetting;
    public final Setting<Boolean> bloomShadowSetting;
    public final Setting<Integer> bloomRadiusSetting;
    public final Setting<Float> bloomStrengthSetting;
    public final Setting<HudColorMode> fadeSetting;
    public final Setting<Color> colorSetting;
    public final Setting<Color> secondColorSetting;
    public final Setting<Integer> fadeOffsetSetting;
    public final Setting<Integer> colorAmountSetting;
    public final Setting<Integer> fadeSpeedSetting;
    public final Setting<Boolean> mixColorsSetting;
    public final List<Setting<Color>> rawColorSettings;
    final InterpolatedFloat paddedChatInputHeight;
    public final InterpolatedFloat chatInputHeight;
    private final Predicate hudEditorVisible = ignored -> {
        if (ClientAccess.evernightMinecraft.screen instanceof ClientScreen screen) {
            return screen.getParentScreen() == null;
        } else {
            return true;
        }
    };
    private final Setting<Boolean> hudEditorSetting;
    private final Setting<Boolean> safeZonesSetting;
    private final Setting<Integer> safeXSetting;
    private final Setting<Integer> safeYSetting;
    private final Setting<Boolean> colorsSetting;
    private final InterpolatedFloat effectIconOffset;
    private final InterpolatedFloat clickGuiSearchOffset;
    private final ChatInputOverlay chatInputOverlay;

    public HUDModule() {
        super("HUD", ModuleCategory.CLIENT);
        boolean editorEnabled = true;
        BooleanSetting editorSetting = new BooleanSetting(
                "HudEditor", editorEnabled, this.hudEditorVisible
        );
        this.hudEditorSetting = this.add(editorSetting);
        EnumSetting<EffectIconMode> iconsSetting = new EnumSetting<>("Icons", HUDModule.EffectIconMode.MOVE);
        this.iconsSetting = this.add(iconsSetting);
        NumberSetting<Float> scaleSetting = new NumberSetting<>(
                "TextScale",
                (1.0F), (0.5F), 2.0F);
        this.textScaleSetting = this.add(scaleSetting);
        BooleanSetting smoothWidthSetting = new BooleanSetting("SmoothWidth", (false));
        this.smoothWidthSetting = this.add(smoothWidthSetting);
        BooleanSetting parallaxSetting = new BooleanSetting("Parallax", (false));
        this.parallaxSetting = this.add(parallaxSetting);
        this.bloomSetting = this.add(new BooleanSetting("Bloom", false).markVisibilityDependency());
        this.bloomShadowSetting = this.add(new BooleanSetting("Shadow", false).dependsOn(this.bloomSetting));
        this.bloomRadiusSetting = this.add(new NumberSetting<>("Radius", 6, 2, 8).dependsOn(this.bloomSetting));
        this.bloomStrengthSetting = this.add(new NumberSetting<>("Strength", 1.0F, 0.5F, 1.5F).dependsOn(this.bloomSetting));
        BooleanSetting safeZonesSetting = new BooleanSetting("SafeZones", (false));
        Setting<Boolean> safeZonesGroup = safeZonesSetting.markAsGroup();
        this.safeZonesSetting = this.add(safeZonesGroup);
        NumberSetting<Integer> safeXOption = new NumberSetting<>(
                "SafeX", 1, 0, 20
        );
        this.safeXSetting = this.add(safeXOption.dependsOn(this.safeZonesSetting));
        NumberSetting<Integer> safeYOption = new NumberSetting<>(
                "SafeY", 1, 0, 10
        );
        this.safeYSetting = this.add(safeYOption.dependsOn(this.safeZonesSetting));
        BooleanSetting colorsSetting = new BooleanSetting("Colors", (true));
        Setting<Boolean> colorsGroup = colorsSetting.markAsGroup();
        this.colorsSetting = this.add(colorsGroup);
        EnumSetting<HudColorMode> fadeSetting = new EnumSetting<>("Fade", HudColorMode.NONE, ignored -> {
            Setting<Boolean> fadeGroup = this.colorsSetting;
            return fadeGroup.isExpanded();
        });
        this.fadeSetting = this.add(fadeSetting);
        NumberSetting<Integer> fadeOffsetOption = new NumberSetting<>(
                "FadeOffset", (5), 1, 30
        );
        this.fadeOffsetSetting = this.add(fadeOffsetOption.dependsOn(this.colorsSetting));
        NumberSetting<Integer> colorAmountSetting = new NumberSetting<>(
                "ColorAmount",
                (5), (2), 9, ignored -> {
            Setting<HudColorMode> amountVisibilityModeSetting = this.fadeSetting;
            return amountVisibilityModeSetting.getValue() == HudColorMode.RAW && this.colorsSetting.isExpanded();
        });
        this.colorAmountSetting = this.add(colorAmountSetting);
        NumberSetting<Integer> fadeSpeedSetting = new NumberSetting<>(
                "FadeSpeed",
                (50), (20), 100, ignored -> {
            Setting<HudColorMode> speedVisibilityModeSetting = this.fadeSetting;
            return speedVisibilityModeSetting.getValue() == HudColorMode.RAW && this.colorsSetting.isExpanded();
        });
        this.fadeSpeedSetting = this.add(fadeSpeedSetting);
        boolean mixColorsEnabled = true;
        BooleanSetting mixColorsSetting = new BooleanSetting("MixColors", mixColorsEnabled, ignored -> {
            Setting<HudColorMode> mixVisibilityModeSetting = this.fadeSetting;
            return mixVisibilityModeSetting.getValue() == HudColorMode.RAW && this.colorsSetting.isExpanded();
        });
        this.mixColorsSetting = this.add(mixColorsSetting);
        ColorSetting primaryColor = new ColorSetting("Color", Catppuccin.MAUVE, ignored -> {
            Setting<HudColorMode> primaryVisibilityModeSetting = this.fadeSetting;
            return primaryVisibilityModeSetting.getValue() != HudColorMode.RAW;
        });
        this.colorSetting = this.add(primaryColor.forceOpaqueColor().dependsOn(this.colorsSetting));
        ColorSetting secondaryColor = new ColorSetting(
                "SecondColor", Catppuccin.SURFACE2, ignored -> {
            Setting<HudColorMode> secondaryVisibilityModeSetting = this.fadeSetting;
            return secondaryVisibilityModeSetting.getValue() == HudColorMode.PULSE
                    && this.colorsSetting.isExpanded();
        }
        );
        Setting<Color> configuredSecondaryColor = secondaryColor.forceOpaqueColor();
        this.secondColorSetting = this.add(configuredSecondaryColor);
        this.rawColorSettings = new ArrayList<>(this.colorAmountSetting.getMaximumValue());
        this.effectIconOffset = new InterpolatedFloat(2.0F);
        this.clickGuiSearchOffset = new InterpolatedFloat(2.0F);
        this.paddedChatInputHeight = new InterpolatedFloat(1.5F);
        this.chatInputHeight = new InterpolatedFloat(1.5F);
        this.chatInputOverlay = new ChatInputOverlay(this);
        Setting<Integer> fadeOffsetVisibilitySetting = this.fadeOffsetSetting;
        Predicate fadeOffsetVisibility = ignored -> {
            Setting<HudColorMode> offsetVisibilityModeSetting = this.fadeSetting;
            if (offsetVisibilityModeSetting.getValue() == HudColorMode.NONE) {
                ColorSetting rainbowColor = (ColorSetting) this.colorSetting;
                return rainbowColor.isRainbow();
            }
            return true;
        };
        fadeOffsetVisibilitySetting.addVisibilityPredicate(fadeOffsetVisibility);
        Setting<Boolean> callbackSetting = this.hudEditorSetting;
        Runnable editorCallback = () -> {
            Setting<Boolean> editorCallbackCallbackEditorSetting = this.hudEditorSetting;
            Boolean editorCallbackEnabled = editorCallbackCallbackEditorSetting.getValue();
            if (!editorCallbackEnabled) {
                this.hudEditorSetting.setValue(true);
                if (!this.isWorldUnavailable()) {
                    Minecraft editorCallbackCapturedMinecraft = ClientAccess.evernightMinecraft;
                    HudEditorScreen editor = ClientContext.getHudEditor();
                    editorCallbackCapturedMinecraft.setScreen(editor);
                }
            }
        };
        callbackSetting.setChangeCallback(editorCallback);
        boolean drawn = false;
        HUDModule drawnOwner = this;
        drawnOwner.setDrawn(drawn);
        this.setToggled(true);
        int colorNumber = 1;
        while (true) {
            Setting<Integer> loopAmountSetting = this.colorAmountSetting;
            Integer loopMaximum = loopAmountSetting.getMaximumValue();
            if (colorNumber > loopMaximum) {
                ChatInputOverlay overlay = this.chatInputOverlay;
                EventBus eventBus = ClientAccess.eventBus;
                eventBus.subscribe(overlay);
                return;
            }
            int capturedColorNumber = colorNumber;
            ColorSetting rawColor = new ColorSetting(
                    "Color" + colorNumber, Color.white, ignored -> {
                Setting<HudColorMode> rawColorVisibilityModeSetting = this.fadeSetting;
                return rawColorVisibilityModeSetting.getValue() == HudColorMode.RAW
                        && this.colorsSetting.isExpanded()
                        && capturedColorNumber <= this.colorAmountSetting.getValue();
            }
            );
            Setting<Color> configuredRawColor = rawColor.forceOpaqueColor();
            Setting<Color> registeredColor = this.add(configuredRawColor);
            this.rawColorSettings.add(registeredColor);
            colorNumber++;
        }
    }

    public static void bindReferencedModules(ModuleRegistry manager) {
        noRenderModule = manager.getModule(NoRenderModule.class);
        HudColorMode.RAINBOW.bindColorsModule(manager);
    }

    public int getSafeX() {
        return this.safeXSetting.getValue();
    }

    public void setSafeX(int value) {
        Setting<Integer> setting = this.safeXSetting;
        Integer boxedValue = value;
        setting.setValue(boxedValue);
    }

    public void updateChatInputHeight(float height) {
        this.paddedChatInputHeight.setTarget(height > 0.0F ? height + 3.0F : 0.0F);
        this.paddedChatInputHeight.updateAndGet();
        this.chatInputHeight.setTarget(height);
        this.chatInputHeight.updateAndGet();
    }

    public float getTextScale() {
        return this.textScaleSetting.getValue();
    }

    public int getSafeY() {
        return this.safeYSetting.getValue();
    }

    public void setSafeY(int value) {
        Setting<Integer> setting = this.safeYSetting;
        Integer boxedValue = value;
        setting.setValue(boxedValue);
    }

    /**
     * Extra bottom margin reserved for the ClickGUI keybind legend. It is added on top of the clamped
     * chat margin because the legend can be several lines tall.
     */
    public float getClickGuiHintOffset() {
        return this.clickGuiSearchOffset.currentValue;
    }

    public float getEffectIconOffset() {
        return this.effectIconOffset.currentValue;
    }

    @EventHandler
    private void onHudRender(HudRenderEvent event) {
        int effectRows = 0;
        Setting<EffectIconMode> iconModeSetting = this.iconsSetting;
        if (iconModeSetting.getValue() == HUDModule.EffectIconMode.MOVE
                && !(ClientAccess.evernightMinecraft.screen instanceof InventoryScreen)) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            Collection activeEffects = player.getActiveEffects();
            Iterator effects = activeEffects.iterator();
            while (effects.hasNext()) {
                MobEffectInstance effectInstance = (MobEffectInstance) effects.next();
                Holder effectHolder = effectInstance.getEffect();
                NoRenderModule noRender = noRenderModule;
                if (noRender.isToggled()) {
                    Setting<Boolean> darknessSetting = noRenderModule.darknessSetting;
                    Boolean hideDarkness = darknessSetting.getValue();
                    if (hideDarkness && effectHolder == MobEffects.DARKNESS) {
                        continue;
                    }
                }
                if (effectRows == 0) {
                    effectRows++;
                }
                Holder classifiedEffectHolder = effectInstance.getEffect();
                MobEffect effect = (MobEffect) classifiedEffectHolder.value();
                if (!effect.isBeneficial()) {
                    effectRows = 2;
                }
            }
        }
        if (ClientAccess.evernightMinecraft.screen != null) {
            Screen screen = ClientAccess.evernightMinecraft.screen;
            if (screen.showsActiveEffects()) {
                effectRows = 0;
            }
        }
        float searchTargetOffset = 0.0F;
        if (ClientAccess.evernightMinecraft.screen instanceof ClickGuiScreen clickGui) {
            // Reserve the whole keybind legend, not a single line, so the HUD clears it while it shows.
            searchTargetOffset = clickGui.getHintHudOffset();
        }
        this.clickGuiSearchOffset.setTarget(searchTargetOffset);
        this.clickGuiSearchOffset.updateAndGet();
        InterpolatedFloat capturedEffectAnimation = this.effectIconOffset;
        float effectTargetOffset = (25) * effectRows;
        capturedEffectAnimation.setTarget(effectTargetOffset);
        this.effectIconOffset.updateAndGet();
        HudWidgetRenderer.renderingHud = true;
        try {
            if (!(ClientAccess.evernightMinecraft.screen instanceof HudEditorScreen)) {
                HudEditorScreen editor = ClientContext.getHudEditor();
                editor.renderWidgets(event.getPoseStack(), -999.0D, -999.0D);
                GuiDrawUtils.flush();
            }
        } finally {
            HudWidgetRenderer.renderingHud = false;
        }
    }

    public boolean shouldDrawTextShadow() {
        return !this.bloomSetting.getValue() || this.bloomShadowSetting.getValue();
    }

    public float getChatInputOffset() {
        return this.paddedChatInputHeight.getValue();
    }

    public Vec3 getParallaxOffset() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (!this.parallaxSetting.getValue() || player == null || player.isSpectator()) return Vec3.ZERO;
        float partialTick = ClientAccess.evernightMinecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float pitchBob = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yawBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
        return new Vec3(-(player.getViewYRot(partialTick) - yawBob) * 0.1F,
                -(player.getViewXRot(partialTick) - pitchBob) * 0.1F, 0.0D);
    }

    public enum ModuleSortMode implements NamedEntry {
        ALPHABET("Alphabet"),
        LENGTH("Length");
        private final String displayName;

        ModuleSortMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    public enum EffectIconMode implements NamedEntry {
        KEEP("Keep"),
        MOVE("Move"),
        HIDE("Hide");
        private final String displayName;

        EffectIconMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}