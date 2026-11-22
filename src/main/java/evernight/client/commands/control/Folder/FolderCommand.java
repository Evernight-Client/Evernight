package evernight.client.commands.control.Folder;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import evernight.client.commands.Command;

public final class FolderCommand extends Command {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public FolderCommand() {
        super("folder");
        String[] aliases = new String[]{"openfolder"};
        this.setAliases(aliases);
    }

    private static int openDirectory(Path directory) {
        if (GraphicsEnvironment.isHeadless()) {
            Util.getPlatform().openPath(directory);
            return 1;
        }
        try {
            Desktop desktop = Desktop.getDesktop();
            File file = directory.toFile();
            desktop.open(file);
        } catch (IOException failure) {
            LOGGER.warn("Could not open folder {}", directory, failure);
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Folder.OpenFailedPrefix")).append(ChatUtils.watermarkText(directory.getFileName().toString())).append(Translations.ui("commands.Folder.OpenFailedSuffix")), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID);
        }
        return 1;
    }

    private static void attachDirectory(ArgumentBuilder parent, ArgumentBuilder child, Path directory) {
        com.mojang.brigadier.Command<Object> executor = context -> openDirectory(directory);
        child.executes(executor);
        if (parent != child) {
            parent.then(child);
        }
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        attachDirectory(builder, builder, ConfigurationManager.ROOT_DIRECTORY);
        attachDirectory(
                builder, Command.literal("spammer"), ConfigurationManager.SPAMMER_DIRECTORY
        );
        attachDirectory(
                builder, Command.literal("killsay"), ConfigurationManager.KILLSAY_DIRECTORY
        );
        attachDirectory(
                builder, Command.literal("textures"), ConfigurationManager.TEXTURES_DIRECTORY
        );
        attachDirectory(
                builder, Command.literal("sounds"), ConfigurationManager.SOUNDS_DIRECTORY
        );
        attachDirectory(
                builder, Command.literal("shaders"), ConfigurationManager.SHADERS_DIRECTORY
        );
        String[] presetAliases = new String[2];
        presetAliases[0] = "configs";
        presetAliases[1] = "presets";
        RequiredArgumentBuilder presetsBuilder = Command.aliasedLiteral(presetAliases);
        for (PresetCategory category : PresetCategory.values()) {
            String categoryName = category.getName();
            Locale locale = Locale.ROOT;
            String literalName = categoryName.toLowerCase(locale);
            LiteralArgumentBuilder categoryBuilder = Command.literal(literalName);
            attachDirectory(presetsBuilder, categoryBuilder, category.getDirectory());
        }
        attachDirectory(builder, presetsBuilder, ConfigurationManager.PRESETS_DIRECTORY);
    }
}