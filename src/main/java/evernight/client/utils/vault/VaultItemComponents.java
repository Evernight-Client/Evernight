package evernight.client.utils.vault;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts an observed {@link ItemStack} into the same item/component specification the Java core
 * generates.
 *
 * <p>The core folds potion, ominous-bottle amplifier, shield damage and enchantments into a 64-bit
 * fingerprint ({@link VaultComponentHash}). Applying the exact same recipe here lets detection
 * distinguish two enchanted books with the same id/count but different enchantments, while older
 * item:count specifications still work (a missing {@code @hash} means "ignore components").
 */
public final class VaultItemComponents {
    private static final Map<String, Integer> ENCHANTMENT_INDEX = new HashMap<>();
    private static final Map<String, Integer> POTION_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < VaultLootData.ENCHANTMENT_NAMES.length; i++) {
            ENCHANTMENT_INDEX.put(VaultLootData.ENCHANTMENT_NAMES[i], i);
        }
        for (int i = 0; i < VaultLootData.POTION_NAMES.length; i++) {
            POTION_INDEX.put(VaultLootData.POTION_NAMES[i], i);
        }
    }

    private VaultItemComponents() {
    }

    /** One {@code item:count@hex} token for the engine's sequence specification. */
    public static String specPart(ItemStack stack) {
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        long components = fingerprint(stack);
        StringBuilder part = new StringBuilder(item.length() + 24);
        part.append(item).append(':').append(stack.getCount());
        if (components != 0L) {
            part.append('@').append(Long.toUnsignedString(components, 16));
        }
        return part.toString();
    }

    /** Component fingerprint, or {@code 0} when the stack has none of the tracked components. */
    public static long fingerprint(ItemStack stack) {
        long hash = 0L;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            hash += enchantment(entry.getKey(), entry.getIntValue());
        }
        for (var entry : stored.entrySet()) {
            hash += enchantment(entry.getKey(), entry.getIntValue());
        }

        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null && potion.potion().isPresent()) {
            String name = potion.potion().get().unwrapKey().orElseThrow().identifier().toString();
            Integer index = POTION_INDEX.get(name);
            if (index != null) {
                hash += VaultComponentHash.potion(index);
            }
        }

        var amplifier = stack.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
        if (amplifier != null) {
            hash += VaultComponentHash.ominousAmplifier(amplifier.value());
        }

        if (stack.is(Items.SHIELD) || stack.getDamageValue() > 0) {
            hash += VaultComponentHash.damage(stack.getDamageValue());
        }
        return VaultComponentHash.finish(hash);
    }

    private static long enchantment(Holder<Enchantment> holder, int level) {
        String name = holder.unwrapKey().orElseThrow().identifier().toString();
        Integer index = ENCHANTMENT_INDEX.get(name);
        if (index == null) {
            return 0L;
        }
        return VaultComponentHash.enchantment(index, level);
    }
}
