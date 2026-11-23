package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.components.ModuleComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.types.ActionSetting;

/**
 * ClickGUI row for an {@link ActionSetting}: shows the label (and optional info text) and runs the
 * action on click. The action usually opens a dedicated editor screen such as the trade manager.
 */
public class ActionSettingComponent extends SettingComponent<Boolean> {
    private final ActionSetting actionSetting;

    public ActionSettingComponent(Panel panel, ModuleComponent moduleComponent, ActionSetting setting) {
        super(panel, moduleComponent, setting);
        this.actionSetting = setting;
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if (!this.isUnavailable()) {
            super.render(poseStack, mouseX, mouseY);
            GuiDrawUtils.fillRect(poseStack, this.panel.getX()
                    + this.getHorizontalPadding(), this.panel.getY() + this.yOffset + 0.5F, this.panel.getX()
                    + this.panel.getWidth() - 1, this.panel.getY() + this.yOffset + this.getHeight()
                    - 0.5F, this.getUiModule().colorSetting.getValue());
            String settingName = this.setting.getDisplayName();
            String infoText = this.actionSetting.getInfoText();
            float textY = this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue()
                    + this.yOffset;
            FontRenderer.INSTANCE.drawWithShadow(poseStack, settingName, this.panel.getX()
                    + 4, textY, this.getUiModule().textColorSetting.getValue());
            if (!infoText.isBlank()) {
                FontRenderer.INSTANCE.drawWithShadow(poseStack, infoText, this.panel.getX()
                        + this.panel.getWidth() - FontRenderer.INSTANCE.getWidth(infoText)
                        - 2.5F, textY, this.getUiModule().textColorSetting.getValue());
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isUnavailable()) {
            super.mouseClicked(mouseX, mouseY, button);
            if (this.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
                this.actionSetting.runAction();
            }
        }
    }
}
