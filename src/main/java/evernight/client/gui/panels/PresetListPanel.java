package evernight.client.gui.panels;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.config.preset.PresetData;
import evernight.client.gui.core.GuiElement;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.preset.PresetScreen;
import evernight.client.gui.components.base.ActionButtonComponent;

public class PresetListPanel extends Panel {
    private final PresetScreen screen;
    private boolean layoutUpdatePending;

    public PresetListPanel(PresetScreen screen) {
        super("List");
        this.screen = screen;
    }

    @Override
    public void render(PoseStack poses, double mouseX, double mouseY) {
        if (this.layoutUpdatePending) {
            for (GuiElement child : this.children) {
                child.update(mouseX, mouseY);
            }
            this.updateLayout();
            this.layoutUpdatePending = false;
        }
        super.render(poses, mouseX, mouseY);
    }

    public void refreshPresets() {
        this.layoutUpdatePending = true;
        this.children.clear();
        ClientContext.configurationManager.getPresetRegistry(this.screen.getSelectedCategory()).forEachPreset(
                preset -> this.addChild(
                        new PresetButton(this.screen, this, preset)));
    }

    private static final class PresetButton extends ActionButtonComponent {
        private final PresetScreen screen;
        private final PresetData preset;

        public PresetButton(PresetScreen screen, Panel panel, PresetData preset) {
            super(panel, preset.getName(), () -> {
                        screen.getPresetPanel().editPreset(preset);
                        screen.getPresetPanel().updateLayout();
                    }
            );
            this.preset = preset;
            this.screen = screen;
        }

        @Override
        public boolean isHighlighted() {
            return this.preset.equals(this.screen.getPresetPanel().getSelectedPreset());
        }
    }
}