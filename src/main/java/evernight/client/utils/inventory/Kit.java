package evernight.client.utils.inventory;

import evernight.client.managers.registry.KitRegistry;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Kit implements NamedEntry {
    private final Map itemsBySlot = new HashMap();
    private final String name;

    public Kit(String name) {
        this.name = name;
    }

    public Map getItemsBySlot() {
        return this.itemsBySlot;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public record ItemDescriptor(int itemId, String potionSignature) {
        public boolean matches(ItemStack stack) {
            Item item = stack.getItem();
            if (Item.getId(item) == this.itemId) {
                return Objects.equals(KitRegistry.getPotionSignature(stack), this.potionSignature);
            }
            return false;
        }
    }
}