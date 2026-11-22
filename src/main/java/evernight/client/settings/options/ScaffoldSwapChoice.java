package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

public enum ScaffoldSwapChoice implements ClientAccess, NamedEntry {
    NONE("None"),
    NORMAL("Normal") {
        @Override
        public void runWithSlot(int slot, Runnable action) {
            InventoryUtils.selectHotbarSlotForPlacement(slot);
            action.run();
        }
    },
    SILENT("Silent") {
        @Override
        public void runWithSlot(int slot, Runnable action) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            Inventory inventory = player.getInventory();
            int originalSlot = inventory.getSelectedSlot();
            InventoryUtils.selectHotbarSlotForPlacement(slot);
            action.run();
            InventoryUtils.selectHotbarSlotForPlacement(originalSlot);
        }
    };
    private final String displayName;

    ScaffoldSwapChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public void runWithSlot(int slot, Runnable action) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == slot) {
            action.run();
        }
    }
}