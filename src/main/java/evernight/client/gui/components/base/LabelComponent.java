package evernight.client.gui.components.base;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.panels.Panel;
import evernight.client.render.font.FontRenderer;

import java.util.function.Supplier;

/**
 * Non-interactive text row for custom panels, used for status and information lines.
 *
 * <p>The text is resolved on every frame so values such as the current fake player name update
 * without rebuilding the panel. Mouse hover is intentionally ignored: a label is not a control.
 */
public final class LabelComponent extends AbstractComponent {
    private final Supplier<String> labelSupplier;

    public LabelComponent(Panel panel, Supplier<String> labelSupplier) {
        super(panel, 0);
        this.labelSupplier = labelSupplier;
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        FontRenderer.INSTANCE.drawWithShadow(poseStack, this.labelSupplier.get(),
                this.panel.getX() + (4),
                this.panel.getY() + this.getTextYOffset() + this.yOffset,
                this.getUiModule().textColorSetting.getValue());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }
}
