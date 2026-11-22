package evernight.client.gui.components.trade;

import evernight.client.ClientAccess;
import evernight.client.gui.panels.Panel;
import evernight.client.render.font.FontRenderer;
import evernight.client.utils.inventory.EnchantmentUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import evernight.client.gui.components.base.AbstractItemListComponent;

public class EnchantmentListComponent extends AbstractItemListComponent<EnchantmentListEntry> {
    static final ItemStack ENCHANTED_BOOK_ICON;

    static {
        ENCHANTED_BOOK_ICON = Items.ENCHANTED_BOOK.getDefaultInstance();
        ENCHANTED_BOOK_ICON.enchant(EnchantmentUtils.getHolder(Enchantments.FORTUNE), 1337);
    }

    public EnchantmentListComponent(Panel panel, Predicate<EnchantmentListEntry> filter) {
        super(panel, filter, 256);
    }

    @Override
    public void update(double mouseX, double mouseY) {
        this.entries.clear();
        Registry<Enchantment> registry = ClientAccess.evernightMinecraft.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> enchantmentKey : registry.registryKeySet()) {
            Identifier enchantmentId = enchantmentKey.identifier();
            Enchantment enchantment = registry.getValue(enchantmentId);
            int maxLevel = enchantment.getMaxLevel();
            String enchantmentName = enchantment.description().getString();
            if (enchantmentName.startsWith("enchantment.")) {
                // A modded enchantment without a language entry falls back to its raw key.
                enchantmentName = FontRenderer.capitalizeWords(enchantmentId.getPath().replace("_", " "));
            }
            for (int level = 1; level <= maxLevel; level++) {
                this.entries.add(new EnchantmentListEntry(enchantmentKey, level, enchantmentName));
            }
        }
        this.entries.removeIf(this.filter.negate());
    }

    @Override
    public String getEntryLabel(EnchantmentListEntry entry) {
        String levelKey = "enchantment.level." + entry.level();
        Language language = Language.getInstance();
        String levelName = language.has(levelKey) ? language.getOrDefault(levelKey)
                : Integer.toString(entry.level());
        Enchantment enchantment = (Enchantment) EnchantmentUtils.getHolder(entry.enchantmentKey()).value();
        if (enchantment.getMaxLevel() == 1) {
            return entry.name();
        }
        return (entry.name() + " \u0001").replaceFirst("\u0001", Matcher.quoteReplacement(levelName));
    }

    @Override
    public ItemStack getEntryIcon(EnchantmentListEntry unusedEntry) {
        return ENCHANTED_BOOK_ICON;
    }
}