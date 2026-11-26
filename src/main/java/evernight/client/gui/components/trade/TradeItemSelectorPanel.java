package evernight.client.gui.components.trade;

import evernight.client.ClientAccess;
import evernight.client.utils.search.SearchNames;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import evernight.client.gui.components.ItemListComponent;

public class TradeItemSelectorPanel extends TradeSelectorPanel<Item> implements ClientAccess {
    private final ItemListComponent itemList = new ItemListComponent(this,
            item -> SearchNames.forItem(item).matches(this.searchQuery()));

    public TradeItemSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
        super(tradeItems, fromSide);
        this.children.add(this.itemList);
    }

    @Override
    protected void applySelection(Item item) {
        if (this.isFromSide()) {
            this.tradeItems.setToStack(Items.EMERALD.getDefaultInstance());
            this.tradeItems.setFromStack(item.getDefaultInstance());
        } else {
            this.tradeItems.setFromStack(Items.EMERALD.getDefaultInstance());
            this.tradeItems.setToStack(item.getDefaultInstance());
        }
    }

    @Override
    protected Item getSelection() {
        return this.itemList.getSelectedEntry();
    }
}