package evernight.client.gui.components.base;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.panels.Panel;
import evernight.client.render.*;
import evernight.client.render.text.Translations;

import java.awt.*;
import java.util.function.Supplier;
import evernight.client.render.color.Catppuccin;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.util.RenderUtils;

public class ButtonComponent extends AbstractComponent {
    private final Supplier<String> labelSupplier;
    private final boolean translateLabel;
    private boolean hoverOffsetEnabled;

    /**
     * @param translateLabel when {@code true} the label is treated as an {@code evernight.ui.*} key suffix;
     *                       when {@code false} it is drawn verbatim (use it for already-resolved text such as
     *                       translated sound names or user-supplied file names)
     */
    public ButtonComponent(Panel parentPanel, Supplier<String> labelSupplier, boolean translateLabel) {
        super(parentPanel, 0);
        this.labelSupplier = labelSupplier;
        this.translateLabel = translateLabel;
    }

    public ButtonComponent(Panel parentPanel, Supplier<String> labelSupplier) {
        this(parentPanel, labelSupplier, true);
    }

    public ButtonComponent(Panel parentPanel, String label, boolean translateLabel) {
        super(parentPanel, 0);
        this.labelSupplier = () -> label;
        this.translateLabel = translateLabel;
    }

    public ButtonComponent(Panel parentPanel, String label) {
        this(parentPanel, label, true);
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        String[] lines = this.getLabel().split("\n");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            float hoverOffset = this.hoverOffsetEnabled && this.isMouseOver(mouseX, mouseY) ? 1.0F : 0.0F;
            String text = this.translateLabel ? Translations.ui(lines[lineIndex]) : lines[lineIndex];
            FontRenderer.INSTANCE.drawWithShadow(poseStack, text, this.panel.getX()
                    + (3), this.panel.getY() + this.yOffset + this.getTextYOffset() + lineIndex
                    * FontRenderer.INSTANCE.getHeight() + 1.0F - hoverOffset, Catppuccin.TEXT);
        }
        super.render(poseStack, mouseX, mouseY);
    }

    @Override
    public int getHeight() {
        return super.getHeight() + FontRenderer.INSTANCE.getHeight()
                * (this.getLabel().split("\n").length - 1)
                + 1;
    }

    protected void renderButtonBackground(PoseStack poseStack) {
        Color buttonColor = this.getUiModule().buttonSetting.getValue();
        buttonColor = ColorUtils.withAlpha(buttonColor, buttonColor.getAlpha() / 255.0F * 0.1F);
        if (this.getUiModule().elementGradientSetting.getValue()) {
            GuiDrawUtils.fillVerticalGradient(poseStack, this.panel.getX() + 1, this.panel.getY()
                    + this.yOffset + 0.5F, this.panel.getWidth() - 1, this.getHeight()
                    - 0.5F, buttonColor.hashCode(), ColorUtils.toArgb(buttonColor, 0));
        } else {
            RenderUtils.INSTANCE.fillRect(poseStack, this.panel.getX() + 1, this.panel.getY() + this.yOffset
                    + 0.5F, this.panel.getX() + this.panel.getWidth() - 1, this.panel.getY() + this.yOffset
                    + this.getHeight() - 0.5F, this.getUiModule().buttonSetting.getValue());
        }
    }

    public String getLabel() {
        return this.labelSupplier.get();
    }

    public void setHoverOffsetEnabled(boolean enabled) {
        this.hoverOffsetEnabled = enabled;
    }
}