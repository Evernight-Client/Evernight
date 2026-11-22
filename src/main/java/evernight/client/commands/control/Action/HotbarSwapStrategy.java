package evernight.client.commands.control.Action;

import evernight.client.ClientAccess;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.world.item.Item;

public class HotbarSwapStrategy implements ActionSwapStrategy {
    @Override
    public SwapSlots findSlots(Item[] items) {
        int sourceSlot = InventoryUtils.findHotbarSlot(items);
        return new SwapSlots(sourceSlot, ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot());
    }

    @Override
    public void apply(SwapSlots slots) {
        InventoryUtils.selectHotbarSlot(slots.sourceSlot());
    }

    @Override
    public void undo(SwapSlots slots) {
        InventoryUtils.selectHotbarSlot(slots.targetSlot());
    }
}