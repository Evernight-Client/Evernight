package evernight.client.modules.hud.Music;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.HudWidgetModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.hud.ResizableHudWidgetRenderer;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;

import java.util.regex.Matcher;
import evernight.client.modules.hud.Welcomer.WelcomerModule;
import evernight.client.modules.hud.MediaSessionBridge;

public class MusicModule extends HudWidgetModule {
    private final Setting<Boolean> prefixSetting;
    private final Setting<String> prefixTextSetting;

    public MusicModule() {
        super("Music");
        this.prefixSetting = this.add(new BooleanSetting("Prefix", false).markVisibilityDependency());
        this.prefixTextSetting = this.add(new StringSetting("Value", "♪").dependsOn(this.prefixSetting));
        ResizableHudWidgetRenderer widget = new ResizableHudWidgetRenderer(this);
        widget.enableHudScale();
        this.setRenderer(widget);
    }

    private boolean shouldSkipMediaRendering() {
        if (!this.isInHudEditor()) {
            int playbackStatus = MediaSessionBridge.playbackStatus;
            return playbackStatus != 4;
        }
        return false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public float[] calculateDimensions() {
        if (!this.shouldSkipMediaRendering()) {
            Class<WelcomerModule> welcomerType = WelcomerModule.class;
            ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
            WelcomerModule welcomer = moduleRegistry.getModule(welcomerType);
            if (!welcomer.shouldHideWidget(this)) {
                float[] dimensions = new float[2];
                FontRenderer textRenderer = FontRenderer.INSTANCE;
                dimensions[0] = textRenderer.getWidth(this.getMusicText());
                dimensions[1] = FontRenderer.INSTANCE.getHeight();
                return dimensions;
            }
        }
        float[] hiddenDimensions = new float[2];
        hiddenDimensions[0] = 0.0F;
        hiddenDimensions[1] = 0.0F;
        return hiddenDimensions;
    }

    @Override
    public void renderContent(PoseStack poses) {
        if (this.shouldSkipMediaRendering()) {
            return;
        }
        Class<WelcomerModule> welcomerType = WelcomerModule.class;
        ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
        WelcomerModule welcomer = moduleRegistry.getModule(welcomerType);
        if (welcomer.shouldHideWidget(this)) {
            return;
        }
        FontRenderer.INSTANCE.drawWithShadow(poses, this.getMusicText(), 0.0F, 0.0F, this.getHudColor(
                this.renderer.getY()));
    }

    private String getMusicText() {
        String text = MediaSessionBridge.getDisplayText();
        if (this.isInHudEditor()) {
            int playbackStatus = MediaSessionBridge.playbackStatus;
            if (playbackStatus != 4) {
                text = "HydrachFM - f3dot";
            }
        }
        Setting<Boolean> prefixOption = this.prefixSetting;
        Boolean prefixEnabled = prefixOption.getValue();
        if (prefixEnabled) {
            Setting<String> prefixTextOption = this.prefixTextSetting;
            String musicText = text;
            String prefixText = String.valueOf(prefixTextOption.getValue());
            text = "\u0001 \u0001"
                    .replaceFirst("\u0001", Matcher.quoteReplacement(prefixText))
                    .replaceFirst("\u0001", Matcher.quoteReplacement(musicText));
        }
        return text.trim();
    }
}