package evernight.client.gui.components.trade;

import evernight.client.ClientAccess;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.trade.TradeEditorScreen;
import evernight.client.gui.screens.trade.TradeManagerScreen;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.StringSetting;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.setting.StringSettingComponent;

public class CreateTradePanel extends Panel implements ClientAccess {
    private final StringSetting nameSetting = new StringSetting("Name", "");
    private final TradeManagerScreen managerScreen;

    public CreateTradePanel(TradeManagerScreen screen) {
        super("Trade");
        this.setX(this.getX() + this.getWidth() + 4);
        this.managerScreen = screen;
        this.registerChild(new StringSettingComponent(this, CloseState.ALWAYS_OPEN, this.nameSetting));
        this.registerChild(
                new ActionButtonComponent(this, "Create", this::createTrade)
        );
    }

    private void createTrade() {
        if (!(this.nameSetting.getValue()).isBlank()) {
            TradeEditorScreen editorScreen = new TradeEditorScreen(this.nameSetting.getValue());
            editorScreen.setParentScreen(this.managerScreen);
            ClientAccess.evernightMinecraft.setScreen(editorScreen);
        }
    }
}