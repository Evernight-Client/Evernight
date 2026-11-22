package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.panels.Panel;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ClampedInterpolatedFloat;

public class BooleanSettingComponent extends SettingComponent<Boolean> {
    private final ClampedInterpolatedFloat enabledAnimation = new ClampedInterpolatedFloat(() -> 2.0F
            * UIModule.INSTANCE.animSpeedSetting.getValue(), true);

    public BooleanSettingComponent(Panel panel, CloseState parentCloseState, Setting<Boolean> setting) {
        super(panel, parentCloseState, setting);
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if (this.isUnavailable()) {
            this.enabledAnimation.reset();
            return;
        }
        this.enabledAnimation.setTarget(this.setting.getValue());
        float enabledProgress = this.enabledAnimation.updateAndGet();
        super.render(poseStack, mouseX, mouseY);
        if ((this.setting.getValue() || enabledProgress > 0.01D) && !this.setting.groupOnly) {
            GuiDrawUtils.fillRect(poseStack, this.panel.getX()
                    + this.getHorizontalPadding(), this.panel.getY() + this.yOffset + 0.5F, this.panel.getX()
                    + Math.max(this.panel.getWidth() * enabledProgress - 1.0F, 2.0F), this.panel.getY()
                    + this.yOffset + this.getHeight() - 0.5F, this.getUiModule().colorSetting.getValue());
        }
        String settingName = this.setting.getDisplayName();
        if (!settingName.isEmpty()) {
            this.renderWithTextScroll(poseStack, settingName, () -> FontRenderer.INSTANCE.drawWithShadow(
                    poseStack, settingName, this.panel.getX() + (4), this.panel.getY() + this.getTextYOffset()
                            - this.getHoverAnimationValue()
                            + this.yOffset, this.getUiModule().textColorSetting.getValue()));
        }
        if (this.setting.visibilityDependency && !this.setting.groupOnly) {
            String expansionIndicator = this.setting.expanded
                    ? "-"
                    : "+";
            FontRenderer.INSTANCE.drawWithShadow(poseStack, expansionIndicator, this.panel.getX()
                    + this.panel.getWidth() - (4)
                    - FontRenderer.INSTANCE.getWidth(expansionIndicator), this.panel.getY()
                    + this.getTextYOffset() - this.getHoverAnimationValue()
                    + this.yOffset, this.getUiModule().textColorSetting.getValue());
        }
        if (this.setting.groupOnly) {
            String groupIndicator = "...";
            FontRenderer.INSTANCE.drawWithShadow(poseStack, groupIndicator, this.panel.getX()
                    + this.panel.getWidth() - (4)
                    - FontRenderer.INSTANCE.getWidth(groupIndicator), this.panel.getY()
                    + this.getTextYOffset() - this.getHoverAnimationValue()
                    + this.yOffset, this.getUiModule().textColorSetting.getValue());
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isUnavailable()) {
            return;
        }
        if (this.setting.groupOnly && button == 0) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (this.isMouseOver(mouseX, mouseY)) {
            if (button == 0) {
                this.setting.setValue(!this.setting.getValue());
            }
            if (button == 1 && this.setting.visibilityDependency) {
                this.setting.expanded = !this.setting.expanded;
            }
        }
    }
}