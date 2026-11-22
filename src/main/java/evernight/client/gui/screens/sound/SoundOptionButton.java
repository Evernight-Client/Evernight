package evernight.client.gui.screens.sound;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.gui.components.base.ButtonComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.render.text.Translations;
import evernight.client.render.util.RenderUtils;
import evernight.client.utils.collections.NamedIdentifier;

class SoundOptionButton extends ButtonComponent {
    private final SoundSelectionScreen screen;
    private final NamedIdentifier soundId;

    public SoundOptionButton(SoundSelectionScreen screen, Panel panel, NamedIdentifier soundId) {
        super(panel, () -> Translations.sound(soundId.getName()), false);
        this.screen = screen;
        this.soundId = soundId;
    }

    @Override
    public void render(PoseStack poseStack, double mouseX, double mouseY) {
        if ((this.screen.getSelectedSetting().getValue()).equals(this.soundId)) {
            RenderUtils.INSTANCE.fillRect(poseStack, this.panel.getX() + 1, this.panel.getY() + this.yOffset
                    + 0.5F, this.panel.getX() + this.panel.getWidth() - 1, this.panel.getY() + this.yOffset
                    + this.getHeight() - 0.5F, this.getUiModule().colorSetting.getValue());
        } else {
            this.renderButtonBackground(poseStack);
        }
        super.render(poseStack, mouseX, mouseY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (this.hovered) {
            if (button == 0) {
                this.screen.getSelectedSetting().setValue(this.soundId);
                this.screen.closeWithTransition();
            } else if (button == 1) {
                ClientContext.soundManager.playSound(this.soundId, 1.0F);
            }
        }
    }
}