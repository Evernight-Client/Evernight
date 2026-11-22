package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.core.CursorType;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.gui.screens.sound.SoundSelectionScreen;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.utils.collections.NamedIdentifier;

import java.util.regex.Matcher;

public class SoundSettingComponent extends SettingComponent<NamedIdentifier> {
    public SoundSettingComponent(Panel panel, CloseState parentCloseState, Setting<NamedIdentifier> setting) {
        super(panel, parentCloseState, setting);
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if (!this.isUnavailable()) {
            super.render(poseStack, mouseX, mouseY);
            if (this.isMouseOver(mouseX, mouseY)) {
                ClientScreen.cursor = CursorType.POINTER;
            }
            GuiDrawUtils.fillRect(poseStack, this.panel.getX()
                    + this.getHorizontalPadding(), this.panel.getY() + this.yOffset + 0.5F, this.panel.getX()
                    + this.panel.getWidth() - 1, this.panel.getY() + this.yOffset + this.getHeight()
                    - 0.5F, this.getUiModule().colorSetting.getValue());
            String settingName = this.setting.getDisplayName();
            String soundName = Translations.sound(this.setting.getValue().getName());
            String label = (settingName + ": \u0001")
                    .replaceFirst("\u0001", Matcher.quoteReplacement(String.valueOf(soundName)));
            this.renderWithTextScroll(poseStack, label, () -> FontRenderer.INSTANCE.drawWithShadow(poseStack,
                    label, this.panel.getX() + (4), this.panel.getY() + this.getTextYOffset()
                            - this.getHoverAnimationValue()
                            + this.yOffset, this.getUiModule().textColorSetting.getValue()));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isUnavailable() && this.isMouseOver(mouseX, mouseY) && button == 0) {
            new SoundSelectionScreen(this.setting, this.getClickGui()).open();
        }
    }
}