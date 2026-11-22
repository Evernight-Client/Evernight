package evernight.client.commands.control.Color;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import evernight.client.ClientContext;
import evernight.client.managers.registry.BlockColorRegistry;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.awt.*;
import java.util.Map;
import evernight.client.commands.arguments.HexColorArgument;
import evernight.client.commands.arguments.RegistryEntryArgument;
import evernight.client.commands.Command;

public final class ColorCommand extends Command {
    public ColorCommand() {
        super("color");
    }

    private static String getBlockName(Block block) {
        DefaultedRegistry registry = BuiltInRegistries.BLOCK;
        Identifier identifier = registry.getKey(block);
        return identifier.toShortLanguageKey();
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        LiteralArgumentBuilder blocksCommand = Command.literal("blocks");
        DefaultedRegistry blockRegistry = BuiltInRegistries.BLOCK;
        ArgumentType blockArgumentType = RegistryEntryArgument.registry(blockRegistry);
        RequiredArgumentBuilder blockArgument = Command.argument("block", blockArgumentType);
        LiteralArgumentBuilder setCommand = Command.literal("set");
        HexColorArgument hexType = new HexColorArgument();
        RequiredArgumentBuilder hexArgument = Command.argument("hex", hexType);
        com.mojang.brigadier.Command<Object> setAction = this::setBlockColor;
        ArgumentBuilder setColorArgument = hexArgument.executes(setAction);
        ArgumentBuilder setBranch = setCommand.then(setColorArgument);
        blockArgument = (RequiredArgumentBuilder) blockArgument.then(setBranch);
        LiteralArgumentBuilder clearCommand = Command.literal("clear");
        com.mojang.brigadier.Command<Object> clearAction = this::clearBlockColor;
        ArgumentBuilder clearBranch = clearCommand.executes(clearAction);
        ArgumentBuilder blockBranches = blockArgument.then(clearBranch);
        LiteralArgumentBuilder listCommand = (LiteralArgumentBuilder) blocksCommand.then(blockBranches);
        com.mojang.brigadier.Command<Object> listAction = this::listBlockColors;
        ArgumentBuilder blocksBranch = listCommand.executes(listAction);
        root.then(blocksBranch);
    }

    private int setBlockColor(CommandContext context) {
        Class<Block> blockType = Block.class;
        Block block = (Block) ((context).getArgument(("block"), blockType));
        Class<Color> colorType = Color.class;
        Color color = (Color) ((context).getArgument(("hex"), colorType));
        Color colorToStore = color;
        Block blockToStore = block;
        BlockColorRegistry registry = ClientContext.blockColorRegistry;
        registry.putColor(blockToStore, colorToStore);
        MutableComponent message = Component.empty();
        Object[] formatArguments = new Object[1];
        formatArguments[0] = getBlockName((block));
        String messagePrefix = Translations.format("commands.Color.SetPrefix", formatArguments);
        message.append(messagePrefix);
        String hexColor = ColorUtils.toHex(color, true);
        MutableComponent colorComponent = Component.literal(hexColor);
        int colorHash = color.hashCode();
        MutableComponent coloredHex = colorComponent.withColor(colorHash);
        message.append(coloredHex);
        message.append(("."));
        ChatUtils.printChat(message, -1);
        return 1;
    }

    private int clearBlockColor(CommandContext context) {
        Class<Block> blockType = Block.class;
        Block block = (Block) ((context).getArgument(("block"), blockType));
        Object clearedColor = null;
        Block blockToClear = block;
        BlockColorRegistry registry = ClientContext.blockColorRegistry;
        registry.putColor(blockToClear, (Color) clearedColor);
        Object[] formatArguments = new Object[1];
        formatArguments[0] = getBlockName((block));
        String messageText = Translations.format("commands.Color.Clear", formatArguments);
        MutableComponent message = Component.literal(messageText);
        ChatUtils.printChat(message, -1);
        return 1;
    }

    private int listBlockColors(CommandContext context) {
        MutableComponent message = Component.empty();
        BlockColorRegistry registry = ClientContext.blockColorRegistry;
        int count = registry.getColors().size();
        String pluralSuffix = count == 1 ? "" : "s";
        String listDelimiter = count == 0 ? "" : ":";
        Object[] formatArguments = new Object[3];
        formatArguments[0] = count;
        formatArguments[1] = pluralSuffix;
        formatArguments[2] = listDelimiter;
        String messagePrefix = Translations.format("commands.Color.ListHeader", formatArguments);
        message.append(messagePrefix);
        registry = ClientContext.blockColorRegistry;
        Map<Block, Color> colors = registry.getColors();
        colors.forEach((block, color) -> {
            String rgbColor = ColorUtils.formatRgb(color, false);
            message.append(("\n"));
            Block blockForName = block;
            String blockName = getBlockName(blockForName);
            MutableComponent colorComponent = Component.literal(blockName);
            ChatFormatting blockNameStyle = ChatFormatting.GRAY;
            MutableComponent styledBlockName = colorComponent.withStyle(blockNameStyle);
            message.append(styledBlockName);
            message.append(Translations.ui("commands.Color.ListSeparator"));
            colorComponent = Component.literal(rgbColor);
            Color colorForHash = color;
            int colorHash = colorForHash.hashCode();
            MutableComponent coloredRgb = colorComponent.withColor(colorHash);
            message.append(coloredRgb);
        });
        ChatUtils.printChat(message, -1);
        return 1;
    }
}