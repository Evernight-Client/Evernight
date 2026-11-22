package evernight.client.modules.render.Blur;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.events.ui.ScreenChangeEvent;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.shader.MaskShaderRenderer;
import evernight.client.render.util.RenderUtils;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.FloatAnimation;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlurModule extends Module {
    private static final Component CLICK_SCREEN_TITLE;

    static {
        String clickTitle = "CLICK";
        CLICK_SCREEN_TITLE = Component.literal(clickTitle);
    }

    private final Setting<Integer> radiusSetting;
    private final FloatAnimation openingAnimation;

    public BlurModule() {
        super("Blur", ModuleCategory.RENDER);
        String radiusKey = "Radius";
        this.radiusSetting = this.add(new NumberSetting<>(radiusKey, 6, 1, 16));
        this.openingAnimation = new FloatAnimation();
        this.setToggled(true);
    }

    private static boolean isBlurExcluded(Screen screen) {
        if (screen == null || screen instanceof ClientScreen || screen instanceof ChatScreen) {
            return true;
        }
        return screen.getTitle().equals(CLICK_SCREEN_TITLE);
    }

    private static void resetOpeningAnimation(FloatAnimation animation) {
        animation.setValue(0.0F);
    }

    public int getRadius() {
        return (this.radiusSetting.getValue());
    }

    @EventHandler
    private void onScreenChange(ScreenChangeEvent.Before event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getNewScreen() == null) {
            FloatAnimation animation = this.openingAnimation;
            resetOpeningAnimation(animation);
            return;
        }
        if (event.getPreviousScreen() != null) {
            return;
        }
        this.openingAnimation.setTarget(1.0F, 75L);
    }

    @EventHandler(priority = -200)
    private void onHudRender(HudRenderEvent.Unconditional event) {
        PoseStack poseStack = event.getPoseStack();
        this.renderBackgroundBlur(poseStack);
    }

    public void renderBackgroundBlur(PoseStack poseStack) {
        Screen currentScreen = ClientAccess.evernightMinecraft.screen;
        if (isBlurExcluded(currentScreen)) {
            return;
        }
        (RenderUtils.getEntityBuffers()).endBatch();
        Runnable drawFullscreenMask = () -> {
            RenderUtils renderer = RenderUtils.INSTANCE;
            float width = (ClientAccess.evernightMinecraft.getWindow()).getGuiScaledWidth();
            float height = (ClientAccess.evernightMinecraft.getWindow()).getGuiScaledHeight();
            renderer.fillRect(poseStack, 0.0F, 0.0F, width, height, 9999, -1);
        };
        float radius = this.getRadius();
        FloatAnimation animationCapture = this.openingAnimation;
        MaskShaderRenderer.renderBlur(drawFullscreenMask, radius * animationCapture.getValue());
    }
}