package evernight.client.gui.components.trade;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.trade.TradeEditorScreen;
import evernight.client.gui.screens.trade.TradeManagerScreen;
import evernight.client.render.hud.TradePanelRequest;
import evernight.client.render.text.Translations;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.inventory.TradeDefinition;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.base.ButtonComponent;

public class TradeActionsPanel extends Panel implements ClientAccess {
    /** How long the button stays armed after the first click, so a slow second click still deletes. */
    private static final long DELETE_CONFIRM_WINDOW_MILLIS = 3000L;
    private final ElapsedTimer deleteConfirmationTimer = new ElapsedTimer();
    private final TradeManagerScreen managerScreen;
    private final TradeDefinition trade;

    public TradeActionsPanel(TradeManagerScreen screen, TradeDefinition selectedTrade) {
        super("Trade");
        this.setX(this.getX() + this.getWidth() + 4);
        this.managerScreen = screen;
        this.trade = selectedTrade;
        this.registerChild(new ButtonComponent(this,
                () -> Translations.ui("hud.TradePrefix") + selectedTrade.getName(), false
        ));
        this.registerChild(
                new ActionButtonComponent(this, "gui.Edit", this::openEditor)
        );
        this.registerChild(new ActionButtonComponent(this, this::getDeleteButtonLabel, this::deleteIfConfirmed)
        );
        this.registerChild(
                new ActionButtonComponent(this, "gui.Cancel", this::showCreatePanel)
        );
    }

    private void openEditor() {
        TradeDefinition registeredTrade = ClientContext.tradeRegistry.findFirst(
                candidate -> candidate.getName()
                        .equals(this.trade.getName())).orElse(null);
        TradeEditorScreen editorScreen = new TradeEditorScreen(registeredTrade);
        editorScreen.setParentScreen(this.managerScreen);
        ClientAccess.evernightMinecraft.setScreen(editorScreen);
    }

    private void deleteIfConfirmed() {
        if (this.deleteConfirmationTimer.hasElapsed(DELETE_CONFIRM_WINDOW_MILLIS)) {
            this.deleteConfirmationTimer.reset();
            return;
        }
        ClientContext.tradeRegistry.unregisterByName(this.trade.name());
        this.showCreatePanel();
    }

    private void showCreatePanel() {
        this.managerScreen.requestPanelChange(TradePanelRequest.forCreate());
    }

    private String getDeleteButtonLabel() {
        return this.deleteConfirmationTimer.hasElapsed(DELETE_CONFIRM_WINDOW_MILLIS)
                ? "gui.Delete"
                : "gui.Are you sure?";
    }
}