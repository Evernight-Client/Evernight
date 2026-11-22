package evernight.client.gui.registry;

import evernight.client.ClientAccess;
import evernight.client.gui.panels.Panel;

class RegistryListPanel extends Panel {
    public RegistryListPanel(String title) {
        super(title);
    }

    @Override
    protected int getHeightLimit() {
        return ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight() / 2 + 10;
    }
}