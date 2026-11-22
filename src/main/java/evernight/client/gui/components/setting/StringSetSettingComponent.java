package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.gui.components.ModuleComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.misc.StringSetScreen;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.types.StringSetSetting;

import java.util.Set;

/**
 * ClickGUI row for a {@link StringSetSetting}: shows the entry count and opens the editor screen.
 *
 * <p>Set settings have no inline editor, so the row behaves like {@code RegistrySetSettingComponent}:
 * the list itself is edited in a dedicated screen where entries are added from a text field and
 * removed by clicking them, which keeps the panel narrow and works for any number of entries.
 */
public class StringSetSettingComponent extends SettingComponent<Set<String>> {
    private final StringSetSetting stringSetSetting;

    public StringSetSettingComponent(Panel panel, ModuleComponent moduleComponent, StringSetSetting setting) {
        super(panel, moduleComponent, setting);
        this.stringSetSetting = setting;
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
            String entryCount = "[%d]".formatted(this.stringSetSetting.getValue().size());
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
                new StringSetScreen(ClientAccess.evernightMinecraft.screen, this.stringSetSetting).open();
            }
        }
    }
}
