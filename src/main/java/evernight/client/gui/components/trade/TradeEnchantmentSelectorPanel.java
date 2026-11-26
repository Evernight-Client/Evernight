package evernight.client.gui.components.trade;

import evernight.client.utils.inventory.EnchantmentUtils;
import evernight.client.utils.search.SearchNames;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

public class TradeEnchantmentSelectorPanel extends TradeSelectorPanel<EnchantmentListEntry> {
    private final EnchantmentListComponent enchantmentList = new EnchantmentListComponent(
            this, this::matchesEnchantment);

    public TradeEnchantmentSelectorPanel(TradeItemsComponent tradeItems, boolean fromSide) {
        super(tradeItems, fromSide);
        this.children.add(this.enchantmentList);
    }

    @Override
    protected void applySelection(EnchantmentListEntry entry) {
        ItemStack stack = this.isFromSide() ? this.tradeItems.getFromStack() : this.tradeItems.getToStack();
        stack.enchant(EnchantmentUtils.getHolder(entry.enchantmentKey()), entry.level());
    }

    @Override
    protected EnchantmentListEntry getSelection() {
        return this.enchantmentList.getSelectedEntry();
    }

    @Override
    public int getWidth() {
        return super.getWidth() + 48;
    }

    private boolean matchesEnchantment(EnchantmentListEntry entry) {
        ItemStack stack = this.isFromSide() ? this.tradeItems.getFromStack() : this.tradeItems.getToStack();
        Enchantment enchantment = (Enchantment) EnchantmentUtils.getHolder(entry.enchantmentKey()).value();
        boolean supported = enchantment.isSupportedItem(stack);
        if (stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK)) {
            supported = true;
        }
        Identifier identifier = entry.enchantmentKey() == null ? null : entry.enchantmentKey().identifier();
        return supported && SearchNames.forEnchantment(enchantment, identifier).matches(this.searchQuery());
    }
}