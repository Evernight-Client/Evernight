package evernight.client.utils.search;

import evernight.client.modules.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the {@link SearchKey} of every searchable object: registry entries (items, blocks, entities,
 * effects, potions, sounds), item stacks with their enchantments and potion effects, enchantments,
 * and client modules.
 *
 * <p>English keys combine the English display name with the registry id, Chinese keys combine the
 * Chinese display name with the current label when that label is actually Chinese. Registry objects
 * are cached, so repeated layout passes only pay for string comparisons.
 */
public final class SearchNames {
    private static final Map<Item, SearchKey> ITEM_KEYS = new ConcurrentHashMap<>();
    private static final Map<Block, SearchKey> BLOCK_KEYS = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, SearchKey> ENTITY_KEYS = new ConcurrentHashMap<>();
    private static final Map<MobEffect, SearchKey> EFFECT_KEYS = new ConcurrentHashMap<>();
    private static final Map<Potion, SearchKey> POTION_KEYS = new ConcurrentHashMap<>();
    private static final Map<SoundEvent, SearchKey> SOUND_KEYS = new ConcurrentHashMap<>();
    private static final Map<Enchantment, SearchKey> ENCHANTMENT_KEYS = new ConcurrentHashMap<>();

    private static final String[] POTION_NAME_SUFFIXES = {
            "potion",
            "splash_potion",
            "lingering_potion",
            "tipped_arrow"
    };

    private SearchNames() {
    }

    /**
     * Builds the key and, when an entry has no Chinese translation at all (untranslated mod entries,
     * data-driven registry values), lets its English names serve Chinese mode too: an entry that cannot
     * be named in Chinese should still be searchable with the only name it has.
     */
    private static SearchKey key(List<String> english, List<String> chinese) {
        if (chinese.isEmpty()) {
            chinese.addAll(english);
        }
        return SearchKey.of(english, chinese);
    }

    public static SearchKey forItem(Item item) {
        return ITEM_KEYS.computeIfAbsent(item, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            addIdentifier(english, BuiltInRegistries.ITEM.getKey(value));
            addTranslation(english, chinese, value.getDescriptionId());
            addLabel(english, chinese, value.getName().getString());
            return key(english, chinese);
        });
    }

    public static SearchKey forBlock(Block block) {
        return BLOCK_KEYS.computeIfAbsent(block, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            addIdentifier(english, BuiltInRegistries.BLOCK.getKey(value));
            addTranslation(english, chinese, value.getDescriptionId());
            addLabel(english, chinese, value.getName().getString());
            return key(english, chinese);
        });
    }

    public static SearchKey forEntityType(EntityType<?> entityType) {
        return ENTITY_KEYS.computeIfAbsent(entityType, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            addIdentifier(english, BuiltInRegistries.ENTITY_TYPE.getKey(value));
            addTranslation(english, chinese, value.getDescriptionId());
            addLabel(english, chinese, value.getDescription().getString());
            return key(english, chinese);
        });
    }

    public static SearchKey forMobEffect(MobEffect effect) {
        return EFFECT_KEYS.computeIfAbsent(effect, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            addIdentifier(english, BuiltInRegistries.MOB_EFFECT.getKey(value));
            addTranslation(english, chinese, value.getDescriptionId());
            addLabel(english, chinese, value.getDisplayName().getString());
            return key(english, chinese);
        });
    }

    public static SearchKey forPotion(Potion potion) {
        return POTION_KEYS.computeIfAbsent(potion, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            Identifier identifier = BuiltInRegistries.POTION.getKey(value);
            addIdentifier(english, identifier);
            if (identifier != null) {
                String prefix = "item." + identifier.getNamespace() + ".";
                for (String suffix : POTION_NAME_SUFFIXES) {
                    addTranslation(english, chinese, prefix + suffix + ".effect." + identifier.getPath());
                }
            }
            Holder<Potion> holder = BuiltInRegistries.POTION.wrapAsHolder(value);
            addLabel(english, chinese, PotionContents.createItemStack(Items.POTION, holder).getHoverName().getString());
            return key(english, chinese);
        });
    }

    public static SearchKey forSound(SoundEvent sound) {
        return SOUND_KEYS.computeIfAbsent(sound, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            addIdentifier(english, value.location());
            addTranslation(english, chinese, "subtitles." + value.location().getNamespace() + "." + value.location().getPath());
            return key(english, chinese);
        });
    }

    public static SearchKey forEnchantment(Enchantment enchantment, Identifier identifier) {
        return ENCHANTMENT_KEYS.computeIfAbsent(enchantment, value -> {
            List<String> english = new ArrayList<>();
            List<String> chinese = new ArrayList<>();
            addIdentifier(english, identifier);
            if (identifier != null) {
                addTranslation(english, chinese, "enchantment." + identifier.getNamespace() + "." + identifier.getPath());
            }
            addLabel(english, chinese, value.description().getString());
            return key(english, chinese);
        });
    }

    /**
     * Item stack search key: the item itself plus a custom hover name, stored/active enchantments and
     * potion effects, so the chest search bar keeps matching everything the item tooltip shows.
     */
    public static SearchKey forItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SearchKey.empty();
        }
        SearchKey base = forItem(stack.getItem());
        List<String> english = new ArrayList<>(List.of(base.english()));
        List<String> chinese = new ArrayList<>(List.of(base.chinese()));
        addLabel(english, chinese, stack.getHoverName().getString());
        addEnchantmentNames(english, chinese, stack.getEnchantments());
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            addEnchantmentNames(english, chinese, stored);
        }
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            for (MobEffectInstance instance : potionContents.getAllEffects()) {
                addTranslation(english, chinese, instance.getDescriptionId());
            }
        }
        return key(english, chinese);
    }

    private static void addEnchantmentNames(List<String> english, List<String> chinese, ItemEnchantments enchantments) {
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            Identifier identifier = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
            SearchKey key = forEnchantment(holder.value(), identifier);
            english.addAll(List.of(key.english()));
            chinese.addAll(List.of(key.chinese()));
        }
    }

    public static SearchKey forModule(Module module) {
        List<String> english = new ArrayList<>(List.of(module.getAliases()));
        List<String> chinese = new ArrayList<>();
        String nameKey = "evernight.names.modules." + module.getName();
        addLabel(english, chinese, LocalizedNames.clientEnglish(nameKey));
        addLabel(english, chinese, LocalizedNames.clientChinese(nameKey));
        addLabel(english, chinese, module.getDisplayName());
        return key(english, chinese);
    }

    /** Registry entries rendered by the set editors (inventory cleaner lists, xray blocks, ...). */
    public static SearchKey forRegistryEntry(Identifier identifier, Object entry, String label) {
        List<String> english = new ArrayList<>();
        List<String> chinese = new ArrayList<>();
        addIdentifier(english, identifier);
        if (entry instanceof Item item) {
            english.addAll(List.of(forItem(item).english()));
            chinese.addAll(List.of(forItem(item).chinese()));
        } else if (entry instanceof Block block) {
            english.addAll(List.of(forBlock(block).english()));
            chinese.addAll(List.of(forBlock(block).chinese()));
        } else if (entry instanceof EntityType<?> entityType) {
            english.addAll(List.of(forEntityType(entityType).english()));
            chinese.addAll(List.of(forEntityType(entityType).chinese()));
        } else if (entry instanceof MobEffect effect) {
            english.addAll(List.of(forMobEffect(effect).english()));
            chinese.addAll(List.of(forMobEffect(effect).chinese()));
        } else if (entry instanceof Potion potion) {
            english.addAll(List.of(forPotion(potion).english()));
            chinese.addAll(List.of(forPotion(potion).chinese()));
        } else if (entry instanceof SoundEvent sound) {
            english.addAll(List.of(forSound(sound).english()));
            chinese.addAll(List.of(forSound(sound).chinese()));
        }
        addLabel(english, chinese, label);
        return key(english, chinese);
    }

    private static void addIdentifier(List<String> english, Identifier identifier) {
        if (identifier == null) {
            return;
        }
        english.add(identifier.toString());
        english.add(identifier.getPath());
        english.add(identifier.getPath().replace('_', ' '));
    }

    private static void addTranslation(List<String> english, List<String> chinese, String translationKey) {
        if (translationKey == null || translationKey.isEmpty()) {
            return;
        }
        String englishName = LocalizedNames.english(translationKey);
        if (englishName != null) {
            english.add(englishName);
        }
        String chineseName = LocalizedNames.chinese(translationKey);
        if (chineseName != null) {
            chinese.add(chineseName);
        }
    }

    /**
     * Routes a display label to the language set it belongs to: labels with Han characters extend the
     * Chinese set (and become pinyin-searchable), ASCII labels extend the English set. This keeps
     * untranslated mod entries searchable without leaking the other language into the active mode.
     */
    private static void addLabel(List<String> english, List<String> chinese, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        label = ChatFormatting.stripFormatting(label);
        boolean hasHan = false;
        boolean hasLatin = false;
        for (int index = 0; index < label.length(); index++) {
            char character = label.charAt(index);
            if (Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN) {
                hasHan = true;
            } else if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')) {
                hasLatin = true;
            }
        }
        if (hasHan) {
            chinese.add(label);
        } else if (hasLatin) {
            english.add(label);
        }
    }
}
