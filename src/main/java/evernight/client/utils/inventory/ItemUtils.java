package evernight.client.utils.inventory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

import java.util.Set;

public final class ItemUtils {
    private static final String VIA_SPEAR_MARKER = "Protocol1_21_11To1_21_9";
    private static final int VIA_SPEAR_ITEM_ID = 1302;
    private static final Component maceDisplayName;
    private static final Set<Component> spearDisplayNames;
    private static ItemStack lastViaSpearStack;
    private static boolean lastViaSpearResult;

    static {
        maceDisplayName = createCompatibilityName("1.21 Mace");
        Component woodenSpearName = createCompatibilityName("1.21.11 Wooden Spear");
        Component stoneSpearName = createCompatibilityName("1.21.11 Stone Spear");
        Component goldenSpearName = createCompatibilityName("1.21.11 Golden Spear");
        Component copperSpearName = createCompatibilityName("1.21.11 Copper Spear");
        Component diamondSpearName = createCompatibilityName("1.21.11 Diamond Spear");
        Component netheriteSpearName = createCompatibilityName("1.21.11 Netherite Spear");
        spearDisplayNames = Set.of(woodenSpearName, stoneSpearName, goldenSpearName, copperSpearName, diamondSpearName, netheriteSpearName);
    }

    private ItemUtils() {
        throw new AssertionError();
    }

    public static boolean isSpear(ItemStack stack) {
        DataComponentType<Component> nameComponent = DataComponents.CUSTOM_NAME;
        Component customName = stack.get(nameComponent);
        if (customName != null && spearDisplayNames.contains(customName)) {
            return true;
        }
        TagKey spearTag = ItemTags.SPEARS;
        return stack.is(spearTag) || isViaSpear(stack);
    }

    /**
     * Detects the 1.21.11 spear that ViaFabricPlus shows as a sword when the connection protocol is
     * downgraded. Via stores the original item id in the stack's custom data; id 1302 is the spear.
     */
    public static boolean isViaSpear(ItemStack stack) {
        if (stack == lastViaSpearStack) {
            return lastViaSpearResult;
        }
        boolean result = computeViaSpear(stack);
        lastViaSpearStack = stack;
        lastViaSpearResult = result;
        return result;
    }

    private static boolean computeViaSpear(ItemStack stack) {
        if (!stack.is(ItemTags.SWORDS)) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        CompoundTag tag = customData.copyTag();
        String serialized = tag.toString();
        return serialized.contains(VIA_SPEAR_MARKER)
                && (serialized.contains("|id:" + VIA_SPEAR_ITEM_ID)
                || serialized.contains("|id\":\"" + VIA_SPEAR_ITEM_ID)
                || serialized.contains("|id\":" + VIA_SPEAR_ITEM_ID)
                || serialized.contains("\"id\":" + VIA_SPEAR_ITEM_ID));
    }

    /**
     * Returns the spear item that matches the material of a Via-simulated sword, or {@code null}
     * when the stack is not a simulated spear.
     */
    public static Item getSpearReplacement(ItemStack stack) {
        if (!isViaSpear(stack)) {
            return null;
        }
        Item item = stack.getItem();
        if (item == Items.WOODEN_SWORD) return Items.WOODEN_SPEAR;
        if (item == Items.STONE_SWORD) return Items.STONE_SPEAR;
        if (item == Items.COPPER_SWORD) return Items.COPPER_SPEAR;
        if (item == Items.IRON_SWORD) return Items.IRON_SPEAR;
        if (item == Items.GOLDEN_SWORD) return Items.GOLDEN_SPEAR;
        if (item == Items.DIAMOND_SWORD) return Items.DIAMOND_SPEAR;
        if (item == Items.NETHERITE_SWORD) return Items.NETHERITE_SPEAR;
        return null;
    }

    /**
     * Returns a render-only copy of a Via-simulated spear with the real spear item type. The
     * original stack must never be mutated: gameplay and packet code still need the sword item that
     * the server understands.
     *
     * <p>{@code ITEM_MODEL} is a default component of every item, so clearing it would leave the stack
     * without a model and nothing would render. The render copy therefore pins the spear's own model,
     * which also discards a model override the translated stack may carry.
     */
    public static ItemStack getSpearRenderStack(ItemStack stack) {
        Item replacement = getSpearReplacement(stack);
        if (replacement == null) {
            return stack;
        }
        ItemStack renderStack = stack.transmuteCopy(replacement);
        renderStack.set(DataComponents.ITEM_MODEL, new ItemStack(replacement).get(DataComponents.ITEM_MODEL));
        return renderStack;
    }

    /**
     * Returns the piercing component of a real spear. Via-simulated swords do not carry the spear
     * component, so the material-matched vanilla spear is used as the fallback.
     */
    public static PiercingWeapon getPiercingWeapon(ItemStack stack) {
        PiercingWeapon weapon = stack.get(DataComponents.PIERCING_WEAPON);
        if (weapon != null) {
            return weapon;
        }
        Item replacement = getSpearReplacement(stack);
        return replacement == null ? null
                : new ItemStack(replacement).get(DataComponents.PIERCING_WEAPON);
    }

    public static boolean isGlass(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock() instanceof StainedGlassBlock || blockItem.getBlock() == Blocks.TINTED_GLASS
                || blockItem.getBlock() == Blocks.GLASS || blockItem.getBlock() instanceof StainedGlassPaneBlock
                || blockItem.getBlock() == Blocks.GLASS_PANE;
    }

    public static boolean isMace(ItemStack stack) {
        DataComponentType<Component> nameComponent = DataComponents.CUSTOM_NAME;
        Component customName = stack.get(nameComponent);
        if (maceDisplayName.equals(customName)) {
            return true;
        }
        Item mace = Items.MACE;
        return stack.is(mace);
    }

    public static boolean isShulkerBox(Item item) {
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    public static boolean isSword(ItemStack stack) {
        if (isViaSpear(stack)) {
            return false;
        }
        TagKey tag = ItemTags.SWORDS;
        return stack.is(tag);
    }

    public static boolean isPickaxe(ItemStack stack) {
        TagKey tag = ItemTags.PICKAXES;
        return stack.is(tag);
    }

    public static boolean isShovel(ItemStack stack) {
        TagKey tag = ItemTags.SHOVELS;
        return stack.is(tag);
    }

    public static boolean isAxe(ItemStack stack) {
        TagKey tag = ItemTags.AXES;
        return stack.is(tag);
    }

    public static boolean isSmithingTemplate(Item item) {
        return item instanceof SmithingTemplateItem;
    }

    public static boolean providesTrimMaterial(Item item) {
        DataComponentMap components = item.components();
        DataComponentType<?> trimMaterialType = DataComponents.PROVIDES_TRIM_MATERIAL;
        return components.has(trimMaterialType);
    }

    public static boolean isFood(Item item) {
        DataComponentMap foodComponents = item.components();
        DataComponentType foodType = DataComponents.FOOD;
        if (foodComponents.has(foodType)) {
            DataComponentMap consumableComponents = item.components();
            DataComponentType consumableType = DataComponents.CONSUMABLE;
            return consumableComponents.has(consumableType);
        }
        return false;
    }

    public static boolean isFood(ItemStack stack) {
        Item item = stack.getItem();
        return isFood(item);
    }

    public static int getFireworkDuration(ItemStack stack) {
        DataComponentType<Fireworks> fireworksComponent = DataComponents.FIREWORKS;
        Fireworks fireworks = stack.get(fireworksComponent);
        if (fireworks != null) {
            int flightDuration = 1 + fireworks.flightDuration();
            return flightDuration * (10) + 1;
        } else {
            return 1;
        }
    }

    private static Component createCompatibilityName(String name) {
        MutableComponent literal = Component.literal(name);
        Style nonItalicStyle = Style.EMPTY.withItalic(false);
        ChatFormatting color = ChatFormatting.WHITE;
        return literal.setStyle(nonItalicStyle.withColor(color));
    }
}