package evernight.client.gui.screens.trade;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.gui.components.trade.TradeEditorPanel;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.utils.inventory.TradeDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import evernight.client.gui.screens.core.ClientScreen;

public class TradeEditorScreen extends ClientScreen {
    private final TradeEditorPanel editorPanel;
    private ItemStack tooltipStack;

    public TradeEditorScreen(String tradeName) {
        TradeDefinition trade = ClientContext.tradeRegistry.createDefault(tradeName);
        this.editorPanel = new TradeEditorPanel(this, trade, "Create");
        this.getPanels().add(this.editorPanel);
    }

    public TradeEditorScreen(TradeDefinition trade) {
        this.editorPanel = new TradeEditorPanel(this, trade, "Edit");
        this.getPanels().add(this.editorPanel);
    }

    @Override
    public void renderContents(
            GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        GuiDrawUtils.flush();
        if (this.tooltipStack != null) {
            graphics.setTooltipForNextFrame(ClientAccess.evernightMinecraft.font, this.tooltipStack, mouseX,
                    mouseY);
        }
        this.tooltipStack = null;
    }

    public void setTooltipStack(ItemStack stack) {
        this.tooltipStack = stack;
    }
}