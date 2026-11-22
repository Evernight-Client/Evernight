package evernight.client.gui.screens.trade;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.gui.components.trade.CreateTradePanel;
import evernight.client.gui.components.trade.TradeActionsPanel;
import evernight.client.gui.components.trade.TradeListPanel;
import evernight.client.gui.panels.Panel;
import evernight.client.render.hud.TradePanelMode;
import evernight.client.render.hud.TradePanelRequest;
import net.minecraft.client.gui.GuiGraphics;
import evernight.client.gui.screens.core.ClientScreen;

public final class TradeManagerScreen extends ClientScreen {
    private final TradeListPanel tradeListPanel = new TradeListPanel(this);
    private Panel actionPanel = new CreateTradePanel(this);
    private TradePanelRequest pendingPanelRequest;

    public TradeManagerScreen() {
        this.tradeListPanel.updateLayout();
        this.getPanels().add(this.tradeListPanel);
        this.getPanels().add(this.actionPanel);
    }

    @Override
    public void renderContents(
            GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (this.pendingPanelRequest != null) {
            this.tradeListPanel.refreshTrades();
            this.getPanels().remove(this.actionPanel);
            if (this.pendingPanelRequest.mode() == TradePanelMode.CREATE) {
                this.actionPanel = new CreateTradePanel(this);
            } else {
                this.actionPanel = new TradeActionsPanel(this, this.pendingPanelRequest.trade());
            }
            this.getPanels().add(this.actionPanel);
            this.pendingPanelRequest = null;
        }
    }

    public void requestPanelChange(TradePanelRequest request) {
        this.pendingPanelRequest = request;
    }
}