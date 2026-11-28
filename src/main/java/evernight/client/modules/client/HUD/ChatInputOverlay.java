package evernight.client.modules.client.HUD;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.FloatAnimation;
import evernight.client.utils.core.InterpolatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

import java.awt.*;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.modules.client.UI.UIModule;

public final class ChatInputOverlay implements ClientAccess {
    private final FloatAnimation commandHighlightAnimation = new FloatAnimation();
    private final HUDModule hudModule;

    public ChatInputOverlay(HUDModule hudModule) {
        this.hudModule = hudModule;
    }

    private static boolean isCommandInput() {
        if (ClientAccess.evernightMinecraft.screen instanceof ChatScreen chatScreen) {
            EditBox input = chatScreen.input;
            String inputText = input.getValue();
            String prefix = CommandRegistry.getPrefix();
            return inputText.startsWith(prefix);
        } else {
            return false;
        }
    }

    @EventHandler
    private void onHudRender(HudRenderEvent.Unconditional event) {
        FloatAnimation animation = this.commandHighlightAnimation;
        boolean commandInput = isCommandInput();
        animation.setTarget(commandInput, 250L);
        animation = this.commandHighlightAnimation;
        float animationValue = animation.getValue();
        float highlightAlpha = animationValue * 1.5F;
        InterpolatedFloat heightAnimation = this.hudModule.chatInputHeight;
        float chatInputHeight = heightAnimation.getValue();
        Minecraft widthMinecraft = ClientAccess.evernightMinecraft;
        Window widthWindow = widthMinecraft.getWindow();
        float screenWidth = widthWindow.getGuiScaledWidth();
        Minecraft heightMinecraft = ClientAccess.evernightMinecraft;
        Window heightWindow = heightMinecraft.getWindow();
        float screenHeight = heightWindow.getGuiScaledHeight();
        Options options = ClientAccess.evernightMinecraft.options;
        int backgroundArgb = options.getBackgroundColor((-2147483648));
        Color backgroundColor = new Color(backgroundArgb, true);
        backgroundColor = ColorUtils.multiplyAlpha(backgroundColor, chatInputHeight / 12.0F);
        PoseStack backgroundPose = event.getPoseStack();
        float backgroundTop = screenHeight - 2.0F - chatInputHeight;
        float backgroundRight = screenWidth - 2.0F;
        GuiDrawUtils.fillRect(
                backgroundPose, 2.0F, backgroundTop, backgroundRight, screenHeight - 2.0F, backgroundColor);
        if (highlightAlpha > 0.0F) {
            Setting<Color> outlineColorSetting = UIModule.INSTANCE.colorSetting;
            Color outlineColor = ColorUtils.multiplyAlpha(outlineColorSetting.getValue(), highlightAlpha);
            PoseStack outlinePose = event.getPoseStack();
            float outlineTop = screenHeight - 3.0F - chatInputHeight;
            float outlineRight = screenWidth - 2.0F;
            GuiDrawUtils.drawRectOutline(
                    outlinePose, 1.0F, outlineTop, outlineRight, screenHeight - 2.0F, outlineColor);
        }
    }
}