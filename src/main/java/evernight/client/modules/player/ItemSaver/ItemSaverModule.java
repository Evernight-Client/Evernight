package evernight.client.modules.player.ItemSaver;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.MultiPlayerGameModeStartDestroyBlockEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemSaverModule extends Module {
    public ItemSaverModule() {
        super("ItemSaver", ModuleCategory.PLAYER);
    }

    public static boolean isSafeToUse(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.has(DataComponents.EQUIPPABLE) || stack.is(Items.ELYTRA)) {
            return true;
        }
        return InventoryUtils.getDurabilityPercent(stack) > 5.0F;
    }

    @EventHandler
    private void onMultiPlayerGameModeStartDestroyBlockEvent(MultiPlayerGameModeStartDestroyBlockEvent event) {
        Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        if (!isSafeToUse(inventory.getItem(selectedSlot))) {
            InventoryUtils.selectHotbarSlotForPlacement(selectedSlot == 8 ? selectedSlot - 1 : selectedSlot + 1);
            event.cancel();
        }
    }
}