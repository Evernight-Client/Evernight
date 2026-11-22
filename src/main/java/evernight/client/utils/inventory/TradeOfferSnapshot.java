package evernight.client.utils.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

public record TradeOfferSnapshot(ItemStack firstCost, ItemStack result, Optional secondCost) {
    public static final TradeOfferSnapshot OUT_OF_STOCK = new TradeOfferSnapshot(null, null, Optional.empty());

    public static TradeOfferSnapshot fromOffer(MerchantOffer offer) {
        ItemStack firstCost = offer.getCostA().copy();
        return new TradeOfferSnapshot(firstCost, offer.assemble(), offer.getItemCostB());
    }
}