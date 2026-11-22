package evernight.client.gui.panels;

import evernight.client.gui.core.GuiElement;
import evernight.client.gui.panels.ModuleCategoryPanel;
import evernight.client.modules.ModuleCategory;

public class HudModulePanel extends ModuleCategoryPanel {
    public HudModulePanel() {
        super(ModuleCategory.HUD);
    }

    @Override
    protected int getHeightLimit() {
        int height = 0;
        for (Object childElement : this.children) {
            GuiElement child = (GuiElement) childElement;
            height += child.getBaseHeight() + 1;
        }
        return height;
    }
}