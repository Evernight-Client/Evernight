package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Tool lookups shared by the automatic mining modules. Shulker box content logic lives in
 * {@link ShulkerBoxUtils}; slot/container math lives in {@link ContainerInventoryMath}.
 */
public final class AutoMiningTools implements ClientAccess {
    private AutoMiningTools() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    public static boolean isPickaxe(ItemStack stack) {
        return ItemUtils.isPickaxe(stack);
    }

    /**
     * @return the first hotbar or inventory slot holding a pickaxe with the given enchantment, or -1
     */
    private static int findEnchantedPickaxe(ResourceKey<Enchantment> enchantment) {
        if (evernightMinecraft.player == null) {
            return -1;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPickaxe(stack) && EnchantmentUtils.getLevel(enchantment, stack) > 0) {
                return slot;
            }
        }
        return -1;
    }

    public static int findFortunePickaxeSlot() {
        return findEnchantedPickaxe(Enchantments.FORTUNE);
    }

    /**
     * @return the first slot holding a silk touch pickaxe, used to mine placed ender chests back
     * without consuming them, or -1
     */
    public static int findSilkTouchPickaxeSlot() {
        return findEnchantedPickaxe(Enchantments.SILK_TOUCH);
    }

    /**
     * @return the first pickaxe whose remaining durability dropped below the threshold, or -1
     */
    public static int findLowDurabilityPickaxe(int threshold) {
        if (evernightMinecraft.player == null) {
            return -1;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isPickaxe(stack)) {
                continue;
            }
            if (ContainerInventoryMath.isDurabilityLow(stack.getMaxDamage(), stack.getDamageValue(), threshold)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return the first slot holding the given item, hotbar first, or -1
     */
    public static int findSlot(Item item) {
        if (evernightMinecraft.player == null) {
            return -1;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean hasEmptyMainSlot() {
        if (evernightMinecraft.player == null) {
            return false;
        }
        Inventory inventory = evernightMinecraft.player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
