package evernight.client.commands.control.Autopreset;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.AutoPresetRegistry;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.inventory.Kit;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.arguments.KitArgumentType;
import evernight.client.commands.arguments.PresetArgumentType;
import evernight.client.commands.arguments.PresetCategoryArgument;
import evernight.client.commands.Command;

public final class AutopresetCommand extends Command {
    public AutopresetCommand() {
        super("autopreset");
        String[] aliases = new String[2];
        aliases[0] = "autoconfig";
        aliases[1] = "autocfg";
        String[] capturedAliases = aliases;
        AutopresetCommand command = this;
        command.setAliases(capturedAliases);
    }

    private int addPreset(CommandContext context) {
        String server = (String) (context.getArgument("server", String.class));
        PresetCategory category = (PresetCategory) (context.getArgument("category", PresetCategory.class));
        String preset = (String) (context.getArgument("preset", String.class));
        AutoPresetRegistry capturedAutopresets = ClientContext.autoPresetRegistry;
        Locale locale = Locale.ROOT;
        String normalizedServer = server.toLowerCase(locale);
        capturedAutopresets.setPreset((normalizedServer), (category), (preset));
        return 1;
    }

    private int addKit(CommandContext context) {
        String server = (String) (context.getArgument("server", String.class));
        Kit kit = (Kit) (context.getArgument("value", Kit.class));
        AutoPresetRegistry capturedAutopresets = ClientContext.autoPresetRegistry;
        Locale locale = Locale.ROOT;
        String normalizedServer = server.toLowerCase(locale);
        String kitName = kit.getName();
        capturedAutopresets.setKit((normalizedServer), kitName);
        return 1;
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        LiteralArgumentBuilder operationLiteral = Command.literal("add");
        ArgumentType addServerNodeType = FancyStringArgument.fancyString();
        RequiredArgumentBuilder currentServerSuggestionsBuilder = Command.argument("server", addServerNodeType);
        SuggestionProvider currentServerSuggestionsProvider = (SuggestionProvider<Object>) this::suggestCurrentServer;
        RequiredArgumentBuilder serverArgument = currentServerSuggestionsBuilder.suggests(currentServerSuggestionsProvider);
        ArgumentType addCategoryNodeType = new PresetCategoryArgument();
        RequiredArgumentBuilder categoryArgument = Command.argument("category", addCategoryNodeType);
        ArgumentType presetNodeType = new PresetArgumentType();
        ArgumentBuilder addPresetActionBuilder = Command.argument("preset", presetNodeType);
        com.mojang.brigadier.Command addPresetActionCallback = (com.mojang.brigadier.Command<Object>) this::addPreset;
        ArgumentBuilder presetAction = addPresetActionBuilder.executes(addPresetActionCallback);
        ArgumentBuilder categoryBranch = categoryArgument.then(presetAction);
        serverArgument = (RequiredArgumentBuilder) serverArgument.then(categoryBranch);
        LiteralArgumentBuilder kitLiteral = Command.literal("kit");
        ArgumentType kitValueNodeType = new KitArgumentType();
        ArgumentBuilder addKitActionBuilder = Command.argument("value", kitValueNodeType);
        com.mojang.brigadier.Command addKitActionCallback = (com.mojang.brigadier.Command<Object>) this::addKit;
        ArgumentBuilder kitAction = addKitActionBuilder.executes(addKitActionCallback);
        ArgumentBuilder kitBranch = kitLiteral.then(kitAction);
        ArgumentBuilder addServerBranch = serverArgument.then(kitBranch);
        ArgumentBuilder addBranch = operationLiteral.then(addServerBranch);
        root.then(addBranch);
        operationLiteral = Command.literal("delete");
        ArgumentType deleteServerNodeType = FancyStringArgument.fancyString();
        RequiredArgumentBuilder configuredServerSuggestionsBuilder = Command.argument("server", deleteServerNodeType);
        SuggestionProvider configuredServerSuggestionsProvider = (SuggestionProvider<Object>) this::suggestConfiguredServers;
        serverArgument = configuredServerSuggestionsBuilder.suggests(configuredServerSuggestionsProvider);
        ArgumentType deleteCategoryNodeType = new PresetCategoryArgument();
        ArgumentBuilder removePresetActionBuilder = Command.argument("category", deleteCategoryNodeType);
        com.mojang.brigadier.Command removePresetActionCallback = (com.mojang.brigadier.Command<Object>) this::removePreset;
        ArgumentBuilder deleteCategoryAction = removePresetActionBuilder.executes(removePresetActionCallback);
        serverArgument = (RequiredArgumentBuilder) serverArgument.then(deleteCategoryAction);
        ArgumentBuilder removeKitActionBuilder = Command.literal("kit");
        com.mojang.brigadier.Command removeKitActionCallback = (com.mojang.brigadier.Command<Object>) this::removeKit;
        ArgumentBuilder deleteKitAction = removeKitActionBuilder.executes(removeKitActionCallback);
        ArgumentBuilder removeServerActionBuilder = serverArgument.then(deleteKitAction);
        com.mojang.brigadier.Command removeServerActionCallback = (com.mojang.brigadier.Command<Object>) this::removeServer;
        ArgumentBuilder deleteServerAction = removeServerActionBuilder.executes(removeServerActionCallback);
        ArgumentBuilder deleteBranch = operationLiteral.then(deleteServerAction);
        root.then(deleteBranch);
    }

    private CompletableFuture suggestCurrentServer(CommandContext context, SuggestionsBuilder builder) {
        ServerStateManager capturedServerState = ClientContext.serverStateManager;
        ServerData serverData = capturedServerState.getServerData();
        if (serverData == null) {
            return builder.buildFuture();
        } else {
            String serverAddress = serverData.ip;
            Iterable currentAddressEntries = Collections.singleton(serverAddress);
            return SharedSuggestionProvider.suggest(currentAddressEntries, builder);
        }
    }

    private int removePreset(CommandContext context) {
        String server = (String) (context.getArgument("server", String.class));
        PresetCategory category = (PresetCategory) (context.getArgument("category", PresetCategory.class));
        PresetCategory categoryToRemove = category;
        String serverToUpdate = server;
        AutoPresetRegistry autopresets = ClientContext.autoPresetRegistry;
        boolean removed = autopresets.removePreset(serverToUpdate, categoryToRemove);
        if (removed) {
            Object[] messageArguments = new Object[1];
            messageArguments[0] = category.getDisplayName();
            String successMessage = Translations.format("commands.Autopreset.PresetRemoved", messageArguments);
            Component presetRemovedMessage = Component.literal(successMessage);
            MessageSignature presetRemovedSignature = ChatUtils.DEFAULT_SIGNATURE;
            ChatUtils.printChat(presetRemovedMessage, presetRemovedSignature);
        } else {
            Component presetRemoveFailureMessage = Component.literal(Translations.ui("commands.Autopreset.PresetRemoveFailed"));
            MessageSignature presetRemoveFailureSignature = ChatUtils.DEFAULT_SIGNATURE;
            ChatUtils.printChat(presetRemoveFailureMessage, presetRemoveFailureSignature);
        }
        return 1;
    }

    private CompletableFuture suggestConfiguredServers(CommandContext context, SuggestionsBuilder builder) {
        AutoPresetRegistry autopresets = ClientContext.autoPresetRegistry;
        Iterable configuredServersEntries = autopresets.getServers();
        return SharedSuggestionProvider.suggest(configuredServersEntries, builder);
    }

    private int removeKit(CommandContext context) {
        String server = (String) (context.getArgument("server", String.class));
        AutoPresetRegistry capturedAutopresets = ClientContext.autoPresetRegistry;
        Locale locale = Locale.ROOT;
        String serverToUpdate = server.toLowerCase(locale);
        capturedAutopresets.removeKit(serverToUpdate);
        return 1;
    }

    private int removeServer(CommandContext context) {
        String server = (String) (context.getArgument("server", String.class));
        AutoPresetRegistry capturedAutopresets = ClientContext.autoPresetRegistry;
        boolean removed = capturedAutopresets.removeServer((server));
        if (removed) {
            Object[] messageArguments = new Object[]{server};
            String successMessage = Translations.format("commands.Autopreset.ServerRemoved", server);
            Component serverRemovedMessage = Component.literal(successMessage);
            MessageSignature serverRemovedSignature = ChatUtils.DEFAULT_SIGNATURE;
            ChatUtils.printChat(serverRemovedMessage, serverRemovedSignature);
        } else {
            Component serverRemoveFailureMessage = Component.literal(Translations.ui("commands.Autopreset.ServerRemoveFailed"));
            MessageSignature serverRemoveFailureSignature = ChatUtils.DEFAULT_SIGNATURE;
            ChatUtils.printChat(serverRemoveFailureMessage, serverRemoveFailureSignature);
        }
        return 1;
    }
}