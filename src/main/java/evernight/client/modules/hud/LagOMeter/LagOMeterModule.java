package evernight.client.modules.hud.LagOMeter;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.modules.HudWidgetModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.hud.ResizableHudWidgetRenderer;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import net.minecraft.client.Minecraft;

import java.awt.*;
import evernight.client.modules.hud.Welcomer.WelcomerModule;

public class LagOMeterModule extends HudWidgetModule {
    private final Setting<Boolean> foliaSetting;
    private final Setting<Color> colorSetting;
    private final ClampedInterpolatedFloat visibilityAnimation;

    public LagOMeterModule() {
        super("Lag'O'Meter", createAliases());
        boolean defaultFolia = false;
        this.foliaSetting = this.add(new BooleanSetting("Folia", defaultFolia));
        this.colorSetting = this.add(new ColorSetting("Color", Color.gray));
        this.visibilityAnimation = new ClampedInterpolatedFloat(1.0F);
        ResizableHudWidgetRenderer renderer = new ResizableHudWidgetRenderer(this);
        renderer.enableHudScale();
        this.setRenderer(renderer);
    }

    private static String[] createAliases() {
        String[] aliasArray = new String[2];
        aliasArray[0] = "lag";
        aliasArray[1] = "lagometer";
        return aliasArray;
    }

    private WelcomerModule getWelcomerModule() {
        Class<WelcomerModule> welcomerType = WelcomerModule.class;
        ModuleRegistry registry = ClientContext.moduleRegistry;
        return registry.getModule(welcomerType);
    }

    private String formatUnresponsiveDuration() {
        Boolean useEntityPackets = this.foliaSetting.getValue();
        ServerStateManager.PacketActivity packetKind = useEntityPackets || RotationManager.is2b2tMode()
                ? ServerStateManager.PacketActivity.ENTITY : ServerStateManager.PacketActivity.ANY;
        long nowMillis = System.currentTimeMillis();
        ServerStateManager packetTracker = ClientContext.serverStateManager;
        float elapsedMillis = (float) (nowMillis - packetTracker.getLastPacketTime(packetKind));
        float elapsedSeconds = elapsedMillis / 1000.0F;
        if (elapsedSeconds >= 3600.0F) {
            return Translations.ui("modules.LagOMeter.MoreThanAnHour");
        } else {
            return "%.1fs".formatted(elapsedSeconds);
        }
    }

    @Override
    public float[] calculateDimensions() {
        String warningText = this.getWarningText();
        if (warningText != null) {
            WelcomerModule welcomer = this.getWelcomerModule();
            if (!welcomer.shouldHideWidget(this)) {
                float[] dimensions = new float[2];
                dimensions[0] = FontRenderer.INSTANCE.getWidth(warningText);
                dimensions[1] = FontRenderer.INSTANCE.getHeight() + 1;
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
        String warningText = this.getWarningText();
        ClampedInterpolatedFloat animationReceiver = this.visibilityAnimation;
        boolean visible = warningText != null;
        animationReceiver.setTarget(visible);
        if (warningText != null) {
            WelcomerModule welcomer = this.getWelcomerModule();
            if (!welcomer.shouldHideWidget(this)) {
                float visibility = this.visibilityAnimation.updateAndGet();
                FontRenderer font = FontRenderer.INSTANCE;
                float slideOffsetY = (1.0F - visibility) * -FontRenderer.INSTANCE.getHeight();
                Setting<Color> colorOption = this.colorSetting;
                font.drawWithShadow(poses, warningText, 0.0F, slideOffsetY, colorOption.getValue());
            }
        }
    }

    private String getWarningText() {
        Boolean useEntityPackets = this.foliaSetting.getValue();
        ServerStateManager.PacketActivity packetKind = useEntityPackets || RotationManager.is2b2tMode()
                ? ServerStateManager.PacketActivity.ENTITY : ServerStateManager.PacketActivity.ANY;
        long nowMillis = System.currentTimeMillis();
        ServerStateManager packetTracker = ClientContext.serverStateManager;
        long elapsedMillis = nowMillis - packetTracker.getLastPacketTime(packetKind);
        long clampedAgeMillis = Math.max(elapsedMillis, 0L);
        boolean remoteServerUnresponsive = false;
        if (clampedAgeMillis > 1000L) {
            Minecraft client = ClientAccess.evernightMinecraft;
            remoteServerUnresponsive = !client.isLocalServer();
        }
        if (!remoteServerUnresponsive && !this.isInHudEditor()) {
            return null;
        }
        String durationText = this.formatUnresponsiveDuration();
        return Translations.ui("hud.ServerNotResponding") + durationText;
    }
}