package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.utils.combat.DamageUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.function.Predicate;

/**
 * Experience bottle repair checks shared by the modules: which items Mending can repair, which of
 * them is low, and where the bottles sit.
 *
 * <p>The mending loop itself is AutoExp (with its tool option), so there is no bottle throwing code
 * here; this class only answers the lookups every caller needs.
 */
public final class ExperienceBottleRepairer implements ClientAccess {
    private ExperienceBottleRepairer() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    // ================= shared repair checks =================

    /**
     * @return whether the stack is a damageable item with Mending, i.e. something experience bottles
     * can repair
     */
    public static boolean isMendingItem(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem()
                && EnchantmentUtils.hasEnchantment(Enchantments.MENDING, stack);
    }

    /**
     * @return whether the mending item is damaged and its remaining durability is below the threshold
     */
    public static boolean needsRepair(ItemStack stack, int durabilityThreshold) {
        return isMendingItem(stack) && stack.getDamageValue() > 0
                && ContainerInventoryMath.isDurabilityLow(stack.getMaxDamage(), stack.getDamageValue(),
                durabilityThreshold);
    }

    /**
     * @return whether the mending item is below the given durability percentage, used by AutoExp
     */
    public static boolean needsRepair(ItemStack stack, float durabilityPercentThreshold) {
        return isMendingItem(stack)
                && DamageUtils.getDurabilityPercent(stack) < durabilityPercentThreshold;
    }

    /**
     * @return the first slot holding a mending tool matching the filter, hotbar first, or -1
     */
    public static int findMendingSlot(Predicate<ItemStack> toolFilter) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (toolFilter.test(stack) && isMendingItem(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return the first slot holding a mending tool below the durability threshold, or -1
     */
    public static int findLowDurabilitySlot(Predicate<ItemStack> toolFilter, int durabilityThreshold) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (toolFilter.test(stack) && needsRepair(stack, durabilityThreshold)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * @return whether any piece of armor (and optionally either hand) is below the durability
     * percentage
     */
    public static boolean anyGearNeedsRepair(boolean includeTools, float durabilityPercentThreshold) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return false;
        }
        for (ItemStack armor : EquipmentUtils.getArmorStacks(player)) {
            if (needsRepair(armor, durabilityPercentThreshold)) {
                return true;
            }
        }
        if (!includeTools) {
            return false;
        }
        return needsRepair(player.getMainHandItem(), durabilityPercentThreshold)
                || needsRepair(player.getOffhandItem(), durabilityPercentThreshold);
    }

    public static int countBottles() {
        return ExperienceBottleUtils.countBottles();
    }

    public static InventoryUtils.SwapSlot findBottleSlot() {
        return InventoryUtils.findSwapSlot(Items.EXPERIENCE_BOTTLE, false);
    }
}
