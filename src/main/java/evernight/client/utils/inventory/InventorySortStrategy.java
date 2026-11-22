package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.settings.options.InventorySorterScopeChoice;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public interface InventorySortStrategy {
    static ItemStack getInventoryStack(int inventorySlot) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        return player.getInventory().getItem(inventorySlot);
    }

    Integer[] getSourceSlots(InventorySorterScopeChoice scope);
}