package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.gui.components.ModuleComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.misc.StringListScreen;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.types.StringListSetting;

import java.util.List;

/**
 * ClickGUI row for a {@link StringListSetting}: shows the entry count and opens the in-game editor.
 */
public class StringListSettingComponent extends SettingComponent<List<String>> {
    private final StringListSetting stringListSetting;

    public StringListSettingComponent(Panel panel, ModuleComponent moduleComponent, StringListSetting setting) {
        super(panel, moduleComponent, setting);
        this.stringListSetting = setting;
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
            String entryCount = "[%d]".formatted(this.stringListSetting.getValue().size());
            float textY = this.panel.getY() + this.getTextYOffset() - this.getHoverAnimationValue()
                    + this.yOffset;
            FontRenderer.INSTANCE.drawWithShadow(poseStack, settingName, this.panel.getX()
                    + 4, textY, this.getUiModule().textColorSetting.getValue());
            FontRenderer.INSTANCE.drawWithShadow(poseStack, entryCount, this.panel.getX()
                    + this.panel.getWidth() - FontRenderer.INSTANCE.getWidth(entryCount)
                    - 2.5F, textY, this.getUiModule().textColorSetting.getValue());
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isUnavailable()) {
            super.mouseClicked(mouseX, mouseY, button);
            if (this.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
                this.stringListSetting.prepare();
                new StringListScreen(ClientAccess.evernightMinecraft.screen, this.stringListSetting).open();
            }
        }
    }
}
