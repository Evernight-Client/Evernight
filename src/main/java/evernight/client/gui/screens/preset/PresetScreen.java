package evernight.client.gui.screens.preset;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.gui.panels.PresetListPanel;
import evernight.client.gui.panels.PresetPanel;
import evernight.client.render.color.Catppuccin;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.util.RenderUtils;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import evernight.client.utils.core.FloatAnimation;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import evernight.client.gui.screens.core.ClientScreen;

public final class PresetScreen extends ClientScreen {
    private final PresetListPanel listPanel = new PresetListPanel(this);
    private final PresetPanel presetPanel = new PresetPanel(this);
    private final FloatAnimation fileDropHintAnimation = new FloatAnimation();
    private PresetCategory selectedCategory = PresetCategory.MODULES;

    public PresetScreen() {
        this.panels.add(this.listPanel);
        this.presetPanel.setX(this.listPanel.getX() + this.listPanel.getWidth() + (3));
        this.panels.add(this.presetPanel);
    }

    @Override
    public void renderContents(
            GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.fileDropHintAnimation.setTarget(!ClientAccess.evernightMinecraft.isWindowActive(), 250L);
        float hintOpacity = this.fileDropHintAnimation.getValue();
        if (hintOpacity != 0.0F) {
            RenderUtils.setColorAlpha(0.75F * hintOpacity);
            String hintText = Translations.ui("gui.Drop preset files here");
            float hintWidth = FontRenderer.INSTANCE.getWidth(hintText);
            FontRenderer.INSTANCE.drawWithShadow(poseStack, hintText,
                    (ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth() - hintWidth)
                            / 2.0F, ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight()
                            / 2.0F, Catppuccin.TEXT);
            RenderUtils.setColorAlpha(1.0F);
        }
    }

    @Override
    public void onFilesDrop(List files) {
        PresetImportScreen.openForDroppedFiles(this, files);
    }

    public PresetListPanel getListPanel() {
        return this.listPanel;
    }

    public PresetPanel getPresetPanel() {
        return this.presetPanel;
    }

    public PresetCategory getSelectedCategory() {
        return this.selectedCategory;
    }

    public void setSelectedCategory(PresetCategory category) {
        this.selectedCategory = category;
    }
}