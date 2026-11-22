package evernight.client.gui.screens.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.utils.core.FloatAnimation;

import java.awt.*;

public final class HudMarginController implements ClientAccess {
    private static final HUDModule hud;

    static {
        hud = ClientContext.getModule(HUDModule.class);
    }

    private final FloatAnimation horizontalOverlayOpacity = new FloatAnimation();
    private final FloatAnimation verticalOverlayOpacity = new FloatAnimation();
    private boolean draggingFromRight;
    private boolean draggingFromBottom;
    private boolean draggingHorizontalMargin;
    private boolean draggingVerticalMargin;

    private boolean isOverHorizontalMargin(double mouseX) {
        return mouseX < hud.getSafeX() + 1 || mouseX
                > ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth() - hud.getSafeX() - 1;
    }

    public void render(PoseStack poseStack, int mouseX, int mouseY) {
        int screenWidth = ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth();
        int screenHeight = ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight();
        boolean showHorizontalMargin = this.isOverHorizontalMargin(mouseX) || this.draggingHorizontalMargin;
        boolean showVerticalMargin = this.isOverVerticalMargin(mouseY) || this.draggingVerticalMargin;
        this.horizontalOverlayOpacity.setTarget(showHorizontalMargin ? 1.4e+02F : 0.0F, 250L);
        this.verticalOverlayOpacity.setTarget(showVerticalMargin && this.horizontalOverlayOpacity.getValue()
                == 0.0F ? 1.4e+02F : 0.0F, 250L);
        if (this.horizontalOverlayOpacity.getValue() > 0.0F) {
            if (this.draggingHorizontalMargin) {
                hud.setSafeX(this.draggingFromRight ? Math.abs(mouseX - screenWidth) : mouseX);
            }
            int horizontalOverlayColor = ColorUtils.toArgb(Color.black, (int) this.horizontalOverlayOpacity
                    .getValue());
            GuiDrawUtils.fillRect(poseStack, 0.0F, 0.0F, hud.getSafeX(), screenHeight, horizontalOverlayColor);
            GuiDrawUtils.fillRect(poseStack, screenWidth
                    - hud.getSafeX(), 0.0F, screenWidth, screenHeight, horizontalOverlayColor);
        } else if (this.verticalOverlayOpacity.getValue() > 0.0F) {
            if (this.draggingVerticalMargin) {
                hud.setSafeY(this.draggingFromBottom ? Math.abs(mouseY - screenHeight) : mouseY);
            }
            int verticalOverlayColor = ColorUtils.toArgb(Color.black, (int) this.verticalOverlayOpacity.getValue()
            );
            GuiDrawUtils.fillRect(poseStack, 0.0F, 0.0F, screenWidth, hud.getSafeY(), verticalOverlayColor);
            GuiDrawUtils.fillRect(poseStack, 0.0F, screenHeight
                    - hud.getSafeY(), screenWidth, screenHeight, verticalOverlayColor);
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingHorizontalMargin = this.isOverHorizontalMargin(mouseX);
            if (!this.draggingHorizontalMargin) {
                this.draggingVerticalMargin = this.isOverVerticalMargin(mouseY);
            }
            this.draggingFromRight = mouseX > ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth()
                    / 2.0F;
            this.draggingFromBottom = mouseY
                    > ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight() / 2.0F;
        }
    }

    public void mouseReleased(int button) {
        if (button == 0) {
            this.draggingHorizontalMargin = false;
            this.draggingVerticalMargin = false;
        }
    }

    private boolean isOverVerticalMargin(double mouseY) {
        return mouseY < hud.getSafeY() + 1 || mouseY
                > ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight() - hud.getSafeY() - 1;
    }
}