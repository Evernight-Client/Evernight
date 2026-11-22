package evernight.client.gui.components.trade;

import net.minecraft.resources.ResourceKey;

public record EnchantmentListEntry(ResourceKey enchantmentKey, int level, String name) {
}