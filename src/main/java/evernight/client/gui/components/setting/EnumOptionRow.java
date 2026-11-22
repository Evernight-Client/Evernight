package evernight.client.gui.components.setting;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.render.font.FontRenderer;
import evernight.client.settings.core.NamedEntry;
import evernight.client.gui.components.base.AbstractComponent;

public class EnumOptionRow<E extends Enum<E> & NamedEntry> extends AbstractComponent {
    final EnumSettingComponent<E> enumComponent;
    final E candidate;
    final int selectableIndex;

    public EnumOptionRow(EnumSettingComponent<E> enumComponent, E candidate, int yOffset, int selectableIndex) {
        super(enumComponent.getPanel(), yOffset);
        this.candidate = candidate;
        this.selectableIndex = selectableIndex;
        this.enumComponent = enumComponent;
    }

    @Override
    public int getHeight() {
        return this.enumComponent.isUnavailable() ? 0 : super.getHeight();
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        super.render(poseStack, mouseX, mouseY);
        String label = this.candidate.getDisplayName();
        FontRenderer.INSTANCE.drawWithShadow(poseStack, label, this.getPanel().getX()
                + this.getPanel().getWidth() / 2.0F - FontRenderer.INSTANCE.getWidth(label)
                / 2.0F, this.enumComponent.getY() + this.yOffset + this.getTextYOffset() - 1.0F
                - this.getHoverAnimationValue(), this.getUiModule().textColorSetting.getValue());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.enumComponent.isUnavailable() || !this.isMouseOver(mouseX, mouseY)
                || !this.enumComponent.isExpanded()) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (this.enumComponent.getParentCloseState().isOpen() && button == 0) {
            this.enumComponent.getSetting().setValue(this.candidate);
            this.enumComponent.setSelectedIndex(this.selectableIndex);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX > this.enumComponent.getX() && mouseX < this.enumComponent.getX()
                + this.getPanel().getWidth() && mouseY > this.enumComponent.getY() + this.yOffset && mouseY
                < this.enumComponent.getY() + this.getBaseHeight() + this.yOffset;
    }
}