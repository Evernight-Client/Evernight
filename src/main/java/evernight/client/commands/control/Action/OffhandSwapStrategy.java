package evernight.client.commands.control.Action;

import evernight.client.ClientAccess;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;

public class OffhandSwapStrategy implements ActionSwapStrategy {
    @Override
    public SwapSlots findSlots(Item[] items) {
        return new SwapSlots(InventoryUtils.toMenuSlot(InventoryUtils.findInventorySlot(items)), 40);
    }

    @Override
    public void apply(SwapSlots slots) {
        this.clickOffhandSlot(slots);
    }

    /**
     * Swapping an item into the offhand slot is its own inverse.
     */
    @Override
    public void undo(SwapSlots slots) {
        this.clickOffhandSlot(slots);
    }

    private void clickOffhandSlot(SwapSlots slots) {
        MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
        int containerId = ClientAccess.evernightMinecraft.player.containerMenu.containerId;
        gameMode.handleInventoryMouseClick(containerId, slots.sourceSlot(), 40, ClickType.SWAP, ClientAccess.evernightMinecraft.player);
    }
}