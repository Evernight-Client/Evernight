package evernight.client.modules.combat.Offhand;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.exploit.XCarry.XCarryModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum OffhandItemChoice implements ClientAccess, NamedEntry {
    Totem("Totem", Items.TOTEM_OF_UNDYING),
    Crystal("Crystal", Items.END_CRYSTAL),
    Gapple("Gapple", Items.GOLDEN_APPLE) {
        @Override
        public Item getItem(boolean useCrapple) {
            return useCrapple ? super.getItem(true) : Items.ENCHANTED_GOLDEN_APPLE;
        }
    },
    Custom("Custom", Items.SHIELD) {
        @Override
        public Item getItem(boolean useCrapple) {
            return (OffhandItemChoice.offhandModule.customSetting.getValue()).stream().findAny().orElse(null);
        }
    };
    private static XCarryModule xCarryModule = ClientContext.getModule(XCarryModule.class);
    private static OffhandModule offhandModule = ClientContext.getModule(OffhandModule.class);
    private final String displayName;
    private final Item defaultItem;

    OffhandItemChoice(String displayName, Item defaultItem) {
        this.displayName = displayName;
        this.defaultItem = defaultItem;
    }

    public static void bindModuleRegistry(ModuleRegistry modules) {
        xCarryModule = modules.getModule(XCarryModule.class);
        offhandModule = modules.getModule(OffhandModule.class);
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public Item getItem(boolean useCrapple) {
        return this.defaultItem;
    }

    public int findContainerSlot(boolean useCrapple) {
        Item itemToFind = this.getItem(useCrapple);
        if (itemToFind == null) return -1;
        if (ClientAccess.evernightMinecraft.player.getOffhandItem().is(itemToFind)) return -1;
        if (ClientAccess.evernightMinecraft.screen instanceof ShulkerBoxScreen || ClientAccess.evernightMinecraft.screen instanceof ContainerScreen) {
            NonNullList<Slot> containerSlots = ClientAccess.evernightMinecraft.player.containerMenu.slots;
            for (Slot slot : containerSlots) {
                ItemStack slotStack = slot.getItem();
                if (slotStack.is(itemToFind)) return slot.index;
            }
        }
        boolean includeXCarry = xCarryModule.isToggled();
        return InventoryUtils.findMenuSlot(itemToFind, includeXCarry);
    }
}