package evernight.client.commands.control.Preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.gui.screens.preset.PresetScreen;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.registry.PresetRegistry;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import evernight.client.commands.arguments.ArrayArgumentType;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.arguments.ModuleArgumentType;
import evernight.client.commands.arguments.ModuleCategoryArgumentType;
import evernight.client.commands.arguments.PresetArgumentType;
import evernight.client.commands.arguments.PresetCategoryArgument;
import evernight.client.config.legacy.LegacyBranding;
import evernight.client.config.preset.ModulePresetCodec;
import evernight.client.config.preset.PresetData;
import evernight.client.config.preset.PresetUndoAction;
import evernight.client.config.preset.PresetUndoState;
import evernight.client.commands.Command;

public final class PresetCommand extends Command {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public PresetCommand() {
        super("preset");
        String[] aliases = new String[(2)];
        aliases[0] = "config";
        aliases[1] = "cfg";
        this.setAliases(aliases);
    }

    public static void reportPresetBinds() {
        MutableComponent message = Component.empty();
        message.append(Translations.ui("commands.Preset.BindsHeader"));
        ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
        Iterator modules = moduleRegistry.getRegistry().iterator();
        while (modules.hasNext()) {
            Module module = (Module) modules.next();
            Keybind keybind = module.getKeybind();
            if (!keybind.isUnbound()) {
                message.append("\n");
                String moduleName = module.getDisplayName();
                message.append(moduleName);
                message.append(" - ");
                String keybindName = keybind.getDisplayName();
                message.append(keybindName);
                message.append(" ");
                Keybind.Mode keybindMode = keybind.mode();
                String modeName = keybindMode.getName();
                message.append(ChatUtils.bracketedText(modeName));
            }
        }
        ChatUtils.printChat(message, -2);
    }

    private static void registerPresetCommands(PresetCommand command, LiteralArgumentBuilder root) {
        RequiredArgumentBuilder categoryArgument = Command.argument("category", new PresetCategoryArgument());
        LiteralArgumentBuilder operationLiteral = Command.literal("save");
        ArgumentBuilder saveNameBranch = Command.argument("preset", FancyStringArgument.fancyString())
                .suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) command::suggestPresetNames)
                .executes((com.mojang.brigadier.Command<Object>) PresetCommand::savePreset);
        ArgumentBuilder saveTree = operationLiteral.then(saveNameBranch);
        categoryArgument = (RequiredArgumentBuilder) categoryArgument.then(saveTree);
        operationLiteral = Command.literal("delete");
        ArgumentBuilder deletePresetBranch = Command.argument("preset", new PresetArgumentType()).executes((com.mojang.brigadier.Command<Object>) PresetCommand::deletePreset);
        ArgumentBuilder deleteTree = operationLiteral.then(deletePresetBranch);
        categoryArgument = (RequiredArgumentBuilder) categoryArgument.then(deleteTree);
        operationLiteral = Command.literal("rename");
        RequiredArgumentBuilder operationArgument = Command.argument("preset", new PresetArgumentType());
        ArgumentBuilder renameTargetBranch = Command.argument("name", StringArgumentType.string()).executes((com.mojang.brigadier.Command<Object>) PresetCommand::renamePreset);
        ArgumentBuilder renamePresetBranch = operationArgument.then(renameTargetBranch);
        ArgumentBuilder renameTree = operationLiteral.then(renamePresetBranch);
        categoryArgument = (RequiredArgumentBuilder) categoryArgument.then(renameTree);
        operationLiteral = Command.literal("load");
        ArgumentBuilder loadPresetBranch = Command.argument("preset", new PresetArgumentType()).executes((com.mojang.brigadier.Command<Object>) context -> {
            ConfigurationManager configurationManager = ClientContext.configurationManager;
            return loadPreset(command, context, configurationManager);
        });
        ArgumentBuilder loadTree = operationLiteral.then(loadPresetBranch);
        categoryArgument = (RequiredArgumentBuilder) categoryArgument.then(loadTree);
        operationLiteral = Command.literal("specific");
        operationArgument = Command.argument("preset", new PresetArgumentType());
        ArgumentBuilder moduleCategoryBranch = Command.argument("module_category", new ModuleCategoryArgumentType()).executes((com.mojang.brigadier.Command<Object>) context -> {
            ConfigurationManager configurationManager = ClientContext.configurationManager;
            return loadCategoryPreset(command, context, configurationManager);
        });
        operationArgument = (RequiredArgumentBuilder) operationArgument.then(moduleCategoryBranch);
        ArgumentBuilder modulesBranch = Command.argument("modules", new ArrayArgumentType(new ModuleArgumentType(), Module.class)).executes((com.mojang.brigadier.Command<Object>) context -> {
            ConfigurationManager configurationManager = ClientContext.configurationManager;
            return loadSelectedModulePresets(command, context, configurationManager);
        });
        ArgumentBuilder specificTree = operationArgument.then(modulesBranch);
        ArgumentBuilder categoryTree = operationLiteral.then(specificTree);
        categoryArgument = (RequiredArgumentBuilder) categoryArgument.then(categoryTree);
        ArgumentBuilder listBranch = Command.literal("list").executes((com.mojang.brigadier.Command<Object>) PresetCommand::listPresets);
        ArgumentBuilder listTree = categoryArgument.then(listBranch);
        LiteralArgumentBuilder rootWithCategories = (LiteralArgumentBuilder) root.then(listTree);
        LiteralArgumentBuilder exportLiteralBuilder = Command.literal("export");
        ArgumentBuilder exportModuleBranch = Command.argument("module", new ModuleArgumentType()).executes((com.mojang.brigadier.Command<Object>) PresetCommand::exportModulePreset);
        ArgumentBuilder exportTree = exportLiteralBuilder.then(exportModuleBranch);
        rootWithCategories = (LiteralArgumentBuilder) rootWithCategories.then(exportTree);
        ArgumentBuilder importBranch = Command.literal("import").executes((com.mojang.brigadier.Command<Object>) context -> {
            try {
                KeyboardHandler keyboard = ClientAccess.evernightMinecraft.keyboardHandler;
                String clipboardConfig = keyboard.getClipboard();
                ConfigurationManager configurationManager = ClientContext.configurationManager;
                importModulePreset(configurationManager, clipboardConfig);
            } catch (Exception importFailure) {
                reportImportFailure(importFailure);
            }
            return 1;
        });
        rootWithCategories.then(importBranch);
        ArgumentBuilder restoreBranch = Command.literal("restore").executes((com.mojang.brigadier.Command<Object>) context -> {
            ConfigurationManager configurationManager = ClientContext.configurationManager;
            return restorePreset(command, configurationManager);
        });
        root.then(restoreBranch);
        root.then(Command.literal("undo").executes(restoreBranch.getCommand()));
        root.executes((com.mojang.brigadier.Command<Object>) context -> {
            command.scheduleRotationTask(() -> {
                Minecraft minecraft = ClientAccess.evernightMinecraft;
                PresetScreen screen = new PresetScreen();
                minecraft.setScreen(screen);
            });
            return 1;
        });
    }

    private static int savePreset(CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String presetName = (String) (context.getArgument("preset", String.class));
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        PresetUndoAction undoAction = PresetUndoAction.beforeSave(presetManager, presetName);
        try {
            presetManager.savePreset(presetName);
            ClientContext.configurationManager.getPresetUndoState().push(undoAction);
        } catch (IOException saveFailure) {
            LOGGER.warn("Could not save preset {}", presetName, saveFailure);
        }
        String savedMessage = Translations.format("commands.Preset.Saved", presetName);
        MutableComponent savedMessageComponent = Component.literal(savedMessage);
        ChatUtils.printChat(savedMessageComponent, ChatUtils.createSignature(-1));
        return 1;
    }

    private static int deletePreset(CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        String presetName = preset.getName();
        PresetUndoAction undoAction = PresetUndoAction.beforeDelete(presetManager, presetName);
        if (presetManager.deletePreset(presetName)) {
            ClientContext.configurationManager.getPresetUndoState().push(undoAction);
        }
        String deletedMessage = Translations.format("commands.Preset.Deleted", preset.getName());
        MutableComponent deletedMessageComponent = Component.literal(deletedMessage);
        ChatUtils.printChat(deletedMessageComponent, ChatUtils.createSignature(-1));
        return 1;
    }

    private static int renamePreset(CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        String oldName = preset.getName();
        String newName = (String) (context.getArgument("name", String.class));
        PresetUndoAction undoAction = PresetUndoAction.beforeRename(presetManager, oldName, newName);
        if (presetManager.renamePreset(oldName, newName)) {
            ClientContext.configurationManager.getPresetUndoState().push(undoAction);
        }
        String reportedOldName = preset.getName();
        String renamedMessage = Translations.format("commands.Preset.Renamed", reportedOldName, context.getArgument("name", String.class));
        MutableComponent renamedMessageComponent = Component.literal(renamedMessage);
        ChatUtils.printChat(renamedMessageComponent, ChatUtils.createSignature(-1));
        return 1;
    }

    private static int loadPreset(PresetCommand owner, CommandContext context, ConfigurationManager configurationManager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetCategory category = (PresetCategory) (context.getArgument("category", PresetCategory.class));
        PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        PresetUndoAction undoAction = PresetUndoAction.beforeLoad(preset.getName());
        presetManager.loadPresets();
        String presetName = preset.getName();
        if (presetManager.applyPreset(presetName)) {
            configurationManager.getPresetUndoState().push(undoAction);
        }
        String loadedMessage = Translations.format("commands.Preset.Loaded", preset.getName());
        MutableComponent loadedMessageComponent = Component.literal(loadedMessage);
        ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1));
        if (category == PresetCategory.ALL) {
            owner.scheduleRotationTask(PresetCommand::reportPresetBinds);
        }
        return 1;
    }

    private static int loadCategoryPreset(PresetCommand owner, CommandContext context, ConfigurationManager configurationManager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        ModuleCategory moduleCategory = (ModuleCategory) (context.getArgument("module_category", ModuleCategory.class));
        presetManager.loadPresets();
        PresetUndoAction undoAction = PresetUndoAction.beforeLoad(preset.getName());
        owner.loadMatchingModules(preset, (Predicate<Module>) candidate -> {
            return candidate.getCategory() == moduleCategory;
        });
        configurationManager.getPresetUndoState().push(undoAction);
        String presetName = preset.getName();
        String loadedMessage = Translations.format("commands.Preset.LoadedFor", presetName, moduleCategory.getDisplayName());
        MutableComponent loadedMessageComponent = Component.literal(loadedMessage);
        ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1));
        return 1;
    }

    private static int loadSelectedModulePresets(PresetCommand owner, CommandContext context, ConfigurationManager configurationManager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetData preset = PresetArgumentType.getPreset(context, "category", "preset");
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        Module[] modules = (Module[]) (context.getArgument("modules", Module[].class));
        Stream moduleClassesStream = Arrays.stream(modules).map((Function<Module, Class>) Object::getClass);
        Set moduleClasses = (Set) moduleClassesStream.collect(Collectors.toSet());
        presetManager.loadPresets();
        Predicate matchesSelectedModule = candidate -> moduleClasses.contains(candidate.getClass());
        PresetUndoAction undoAction = PresetUndoAction.beforeLoad(preset.getName());
        owner.loadMatchingModules(preset, matchesSelectedModule);
        configurationManager.getPresetUndoState().push(undoAction);
        String presetName = preset.getName();
        String loadedMessage = Translations.format("commands.Preset.LoadedFor", presetName, owner.joinSelectedModuleNames(matchesSelectedModule));
        MutableComponent loadedMessageComponent = Component.literal(loadedMessage);
        ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1));
        return 1;
    }

    private static int listPresets(CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetRegistry presetManager = PresetCategoryArgument.getManager(context, "category");
        MutableComponent listMessage = Component.literal(Translations.ui("commands.Preset.ListHeader"));
        listMessage = listMessage.append(ComponentUtils.formatList(presetManager.getRegistry(), preset -> {
            String presetName = preset.getName();
            return Component.literal(presetName);
        }));
        listMessage = listMessage.append(Translations.ui("commands.Preset.ListSuffix"));
        ChatUtils.printChat(listMessage, ChatUtils.createSignature(-1));
        return 1;
    }

    private static int exportModulePreset(CommandContext context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Module module = (Module) (context.getArgument("module", Module.class));
        ConfigurationManager configurationManager = ClientContext.configurationManager;
        ModulePresetCodec moduleConfigCodec = configurationManager.getModulePresetCodec();
        KeyboardHandler keyboard = ClientAccess.evernightMinecraft.keyboardHandler;
        String encodedModuleConfig = moduleConfigCodec.encode(module);
        keyboard.setClipboard(encodedModuleConfig);
        MutableComponent messagePrefix = Component.literal(Translations.ui("commands.Preset.ExportedPrefix"));
        String moduleName = module.getDisplayName();
        MutableComponent moduleNameComponent = Component.literal(moduleName);
        ChatFormatting nameColor = ChatFormatting.GRAY;
        MutableComponent messageWithModuleName = messagePrefix.append(moduleNameComponent.withStyle(nameColor));
        MutableComponent exportedMessage = messageWithModuleName.append(Translations.ui("commands.Preset.ExportedSuffix"));
        ChatUtils.printChat(exportedMessage, ChatUtils.createSignature(-1));
        return 1;
    }

    private static void importModulePreset(ConfigurationManager configurationManager, String clipboardConfig) {
        ModulePresetCodec moduleConfigCodec = configurationManager.getModulePresetCodec();
        ModulePresetCodec.DecodedPreset decodedConfig = moduleConfigCodec.decode(clipboardConfig);
        Module module = decodedConfig.module();
        JsonObject moduleConfig = decodedConfig.json();
        module.fromJson(moduleConfig);
        MutableComponent messagePrefix = Component.literal(Translations.ui("commands.Preset.ImportedPrefix"));
        String moduleName = module.getDisplayName();
        MutableComponent moduleNameComponent = Component.literal(moduleName);
        ChatFormatting nameColor = ChatFormatting.GRAY;
        MutableComponent messageWithModuleName = messagePrefix.append(moduleNameComponent.withStyle(nameColor));
        MutableComponent importedMessage = messageWithModuleName.append(Translations.ui("commands.Preset.ImportedSuffix"));
        ChatUtils.printChat(importedMessage, ChatUtils.createSignature(-1));
    }

    private static void reportImportFailure(Exception importFailure) {
        MutableComponent errorMessage = Component.literal(Translations.ui("commands.Preset.ImportFailed"));
        StackTraceElement failureLocation = importFailure.getStackTrace()[0];
        String failureLocationText = failureLocation.toString();
        if (!failureLocationText.contains("Base64") && !(importFailure instanceof IllegalArgumentException)) {
            errorMessage = errorMessage.append(Translations.ui("commands.Preset.ImportUnknownReason"));
        } else {
            errorMessage = errorMessage.append(Translations.ui("commands.Preset.ImportInvalidInput"));
        }
        ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.MID);
        LOGGER.warn("Could not import preset", importFailure);
    }

    private static int restorePreset(PresetCommand owner, ConfigurationManager configurationManager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PresetUndoState restoreManager = configurationManager.getPresetUndoState();
        PresetUndoAction action;
        try {
            action = restoreManager.undo();
        } catch (IOException undoFailure) {
            LOGGER.warn("Could not undo preset change", undoFailure);
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Preset.UndoFailed")), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID);
            return 1;
        }
        if (action == null) {
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Preset.NothingToUndo")), ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID);
            return 1;
        }
        owner.scheduleRotationTask(() -> ChatUtils.printChat(Component.literal(formatUndoAction(action)), ChatUtils.DEFAULT_SIGNATURE));
        return 1;
    }

    private static String formatUndoAction(PresetUndoAction action) {
        return switch (action) {
            case PresetUndoAction.Load load ->
                    Translations.format("commands.Preset.UndoLoad", load.name());
            case PresetUndoAction.Save save ->
                    Translations.format("commands.Preset.UndoSave", save.name());
            case PresetUndoAction.Delete delete ->
                    Translations.format("commands.Preset.UndoDelete", delete.preset().getName());
            case PresetUndoAction.Rename rename ->
                    Translations.format("commands.Preset.UndoRename", rename.oldName(), rename.newName());
        };
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        registerPresetCommands(this, root);
    }

    private String joinSelectedModuleNames(Predicate matchesModule) {
        ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
        Stream moduleNames = moduleRegistry.getRegistry().stream().filter(matchesModule).map((Function<Module, String>) Module::getDisplayName);
        return (String) moduleNames.collect(Collectors.joining(", "));
    }

    private void loadMatchingModules(PresetData preset, Predicate matchesModule) {
        ModuleRegistry moduleRegistry = ClientContext.moduleRegistry;
        Iterator modules = moduleRegistry.getRegistry().iterator();
        while (modules.hasNext()) {
            Module module = (Module) modules.next();
            if (!(module instanceof HudWidgetModule) && matchesModule.test(module)) {
                try {
                    JsonObject presetContents = preset.contents();
                    JsonObject contentsObject = presetContents.getAsJsonObject();
                    String moduleConfigName = module.getConfigName();
                    JsonElement moduleConfig = LegacyBranding.findModuleConfig(contentsObject, moduleConfigName);
                    module.fromJson(moduleConfig);
                } catch (Exception ignoredLoadFailure) {
                }
            }
        }
    }

    private CompletableFuture suggestPresetNames(CommandContext context, SuggestionsBuilder builder) {
        return (new PresetArgumentType()).listSuggestions(context, builder);
    }
}