package evernight.client.gui.components.trade;

import evernight.client.ClientContext;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.trade.TradeManagerScreen;
import evernight.client.render.hud.TradePanelRequest;
import evernight.client.utils.inventory.TradeDefinition;

import java.util.List;
import evernight.client.gui.components.base.ActionButtonComponent;

public class TradeListPanel extends Panel {
    private final TradeManagerScreen managerScreen;

    public TradeListPanel(TradeManagerScreen screen) {
        super("List");
        this.managerScreen = screen;
        this.refreshTrades();
    }

    @Override
    public void init() {
        super.init();
        this.refreshTrades();
    }

    public void refreshTrades() {
        this.getChildren().clear();
        for (Object registeredEntry : ClientContext.tradeRegistry.getRegistry()) {
            TradeDefinition registeredTrade = (TradeDefinition) registeredEntry;
            this.registerChild(
                    new ActionButtonComponent(this, registeredTrade.name(), () -> this.selectTrade(registeredTrade)));
        }
        this.updateLayout();
    }

    private void selectTrade(TradeDefinition selectedTrade) {
        this.managerScreen.requestPanelChange(TradePanelRequest.forEdit(selectedTrade));
    }
}