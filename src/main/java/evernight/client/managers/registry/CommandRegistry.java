package evernight.client.managers.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.commands.movement.Baritone.BaritoneCommand;
import evernight.client.commands.Command;
import evernight.client.commands.social.EnemyFriend.EnemyFriendCommand;
import evernight.client.commands.control.Module.ModuleCommand;
import evernight.client.events.ui.CommandExecuteEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.input.KeyboardHandlerCharTypedEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.Module;
import evernight.client.modules.client.Settings.SettingsModule;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.SocialRole;
import evernight.client.utils.core.KeyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import evernight.client.commands.control.Action.ActionCommand;
import evernight.client.commands.control.Alias.AliasCommand;
import evernight.client.commands.control.Alloff.AlloffCommand;
import evernight.client.commands.control.Autopreset.AutopresetCommand;
import evernight.client.commands.control.Bind.BindCommand;
import evernight.client.commands.control.Color.ColorCommand;
import evernight.client.commands.control.Defaults.DefaultsCommand;
import evernight.client.commands.control.Drawn.DrawnCommand;
import evernight.client.commands.control.Folder.FolderCommand;
import evernight.client.commands.control.Glint.GlintCommand;
import evernight.client.commands.control.Highlight.HighlightCommand;
import evernight.client.commands.control.Macro.MacroCommand;
import evernight.client.commands.control.Panic.PanicCommand;
import evernight.client.commands.control.Prefix.PrefixCommand;
import evernight.client.commands.control.Preset.PresetCommand;
import evernight.client.commands.control.Reload.ReloadCommand;
import evernight.client.commands.control.Toggle.ToggleCommand;
import evernight.client.commands.control.Webhook.WebhookCommand;
import evernight.client.commands.misc.Disconnect.DisconnectCommand;
import evernight.client.commands.misc.Fakeplayer.FakeplayerCommand;
import evernight.client.commands.misc.Help.HelpCommand;
import evernight.client.commands.misc.Playtime.PlaytimeCommand;
import evernight.client.commands.misc.Queue.QueueCommand;
import evernight.client.commands.misc.Session.SessionCommand;
import evernight.client.commands.misc.Wait.WaitCommand;
import evernight.client.commands.movement.Angle.AngleCommand;
import evernight.client.commands.movement.Clip.ClipCommand;
import evernight.client.commands.movement.Fireworks.FireworksCommand;
import evernight.client.commands.movement.Pitch.PitchCommand;
import evernight.client.commands.movement.Yaw.YawCommand;
import evernight.client.commands.player.Autopot.AutopotCommand;
import evernight.client.commands.player.Cheststealer.CheststealerCommand;
import evernight.client.commands.player.Kit.KitCommand;
import evernight.client.commands.player.Trade.TradeCommand;
import evernight.client.commands.social.Chatfilter.ChatfilterCommand;
import evernight.client.commands.social.Global.GlobalCommand;
import evernight.client.commands.social.Namehistory.NamehistoryCommand;
import evernight.client.commands.social.Plugins.PluginsCommand;
import evernight.client.commands.social.Print.PrintCommand;
import evernight.client.commands.social.Say.SayCommand;
import evernight.client.commands.social.Seen.SeenCommand;
import evernight.client.commands.world.Coords.CoordsCommand;
import evernight.client.commands.world.Nbt.NbtCommand;
import evernight.client.commands.world.Waypoints.WaypointsCommand;
import evernight.client.managers.command.CommandAliasBuilder;
import evernight.client.managers.command.ModuleAliasBuilder;
import evernight.client.modules.misc.Macro;
import evernight.client.utils.inventory.Kit;

public final class CommandRegistry extends Registry<Command> implements ClientAccess, JsonConfigurable {
    public static final SharedSuggestionProvider suggestionSource = new ClientSuggestionProvider(
            null, ClientAccess.evernightMinecraft, ignored -> true);
    public static final CommandDispatcher commandDispatcher = new CommandDispatcher();
    private static final SettingsModule settingsModule = ClientContext.getModule(SettingsModule.class);
    private static String commandPrefix = ";";

    public CommandRegistry() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
        this.registerCommand(new evernight.client.commands.control.Action.ActionCommand());
        this.registerCommand(new evernight.client.commands.control.Alias.AliasCommand());
        this.registerCommand(new evernight.client.commands.control.Alloff.AlloffCommand());
        this.registerCommand(new evernight.client.commands.movement.Angle.AngleCommand());
        this.registerCommand(new evernight.client.commands.player.Autopot.AutopotCommand());
        this.registerCommand(new evernight.client.commands.control.Autopreset.AutopresetCommand());
        this.registerCommand(new evernight.client.commands.control.Bind.BindCommand());
        this.registerCommand(new evernight.client.commands.social.Chatfilter.ChatfilterCommand());
        this.registerCommand(new evernight.client.commands.player.Cheststealer.CheststealerCommand());
        this.registerCommand(new evernight.client.commands.movement.Clip.ClipCommand());
        this.registerCommand(new evernight.client.commands.control.Color.ColorCommand());
        this.registerCommand(new evernight.client.commands.world.Coords.CoordsCommand());
        this.registerCommand(new evernight.client.commands.control.Defaults.DefaultsCommand());
        this.registerCommand(new evernight.client.commands.misc.Disconnect.DisconnectCommand());
        this.registerCommand(new evernight.client.commands.control.Drawn.DrawnCommand());
        this.registerCommand(new evernight.client.commands.misc.Fakeplayer.FakeplayerCommand());
        this.registerCommand(new evernight.client.commands.movement.Fireworks.FireworksCommand());
        this.registerCommand(new evernight.client.commands.control.Folder.FolderCommand());
        this.registerCommand(new evernight.client.commands.control.Glint.GlintCommand());
        this.registerCommand(new evernight.client.commands.social.Global.GlobalCommand());
        this.registerCommand(new evernight.client.commands.misc.Help.HelpCommand());
        this.registerCommand(new evernight.client.commands.control.Highlight.HighlightCommand());
        this.registerCommand(new evernight.client.commands.player.Kit.KitCommand());
        this.registerCommand(new evernight.client.commands.control.Macro.MacroCommand());
        this.registerCommand(new evernight.client.commands.social.Namehistory.NamehistoryCommand());
        this.registerCommand(new evernight.client.commands.world.Nbt.NbtCommand());
        this.registerCommand(new evernight.client.commands.control.Panic.PanicCommand());
        this.registerCommand(new evernight.client.commands.movement.Pitch.PitchCommand());
        this.registerCommand(new evernight.client.commands.misc.Playtime.PlaytimeCommand());
        this.registerCommand(new evernight.client.commands.social.Plugins.PluginsCommand());
        this.registerCommand(new evernight.client.commands.control.Prefix.PrefixCommand());
        this.registerCommand(new evernight.client.commands.control.Preset.PresetCommand());
        this.registerCommand(new evernight.client.commands.social.Print.PrintCommand());
        this.registerCommand(new evernight.client.commands.misc.Queue.QueueCommand());
        this.registerCommand(new evernight.client.commands.control.Reload.ReloadCommand());
        this.registerCommand(new evernight.client.commands.social.Say.SayCommand());
        this.registerCommand(new evernight.client.commands.social.Seen.SeenCommand());
        this.registerCommand(new evernight.client.commands.misc.Session.SessionCommand());
        this.registerCommand(new evernight.client.commands.control.Toggle.ToggleCommand());
        this.registerCommand(new evernight.client.commands.player.Trade.TradeCommand());
        this.registerCommand(new evernight.client.commands.misc.Wait.WaitCommand());
        this.registerCommand(new evernight.client.commands.world.Waypoints.WaypointsCommand());
        this.registerCommand(new evernight.client.commands.control.Webhook.WebhookCommand());
        this.registerCommand(new evernight.client.commands.movement.Yaw.YawCommand());
        this.registerPathfindingCommand();
        this.registerRelationshipCommands();
        this.registerModuleAliasCommands();
        this.entries.sort(Comparator.comparing(Command::getName));
    }

    public static char getPrefixCharacter() {
        return commandPrefix.charAt(0);
    }

    public static void executeCommand(String input) {
        CommandExecuteEvent event = new CommandExecuteEvent(input);
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.post(event);
        if (event.isCancelled()) {
            return;
        }
        try {
            CommandDispatcher executionDispatcher = commandDispatcher;
            SharedSuggestionProvider source = suggestionSource;
            ParseResults parsed = commandDispatcher.parse(input, source);
            executionDispatcher.execute(parsed);
        } catch (CommandSyntaxException ignoredFailure) {
        }
    }

    public static String getPrefix() {
        return commandPrefix;
    }

    public static void setPrefix(String prefix) {
        if (prefix.isBlank()) {
            return;
        }
        String updatedPrefix = prefix.substring(0, 1);
        commandPrefix = updatedPrefix;
        Setting<String> prefixSetting = settingsModule.prefixSetting;
        prefixSetting.setValue(updatedPrefix);
    }

    private void registerRelationshipCommands() {
        this.registerCommand(new EnemyFriendCommand(SocialRole.FRIEND));
        this.registerCommand(new EnemyFriendCommand(SocialRole.ENEMY));
    }

    private void registerPathfindingCommand() {
        this.registerCommand(new BaritoneCommand());
    }

    @Override
    public boolean register(Command command) {
        return this.registerCommand(command);
    }

    @Override
    public void fromJson(JsonElement config) {
        JsonObject objectForPresence = config.getAsJsonObject();
        if (objectForPresence.has("prefix")) {
            JsonObject objectForValue = config.getAsJsonObject();
            JsonElement prefixElement = objectForValue.get("prefix");
            String prefix = prefixElement.getAsString();
            setPrefix(prefix);
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject config = new JsonObject();
        String prefix = commandPrefix;
        config.addProperty("prefix", prefix);
        return config;
    }

    @Override
    public String getConfigName() {
        return "commands.json";
    }

    @EventHandler
    public void onKeyboardHandlerCharTypedEvent(KeyboardHandlerCharTypedEvent inputEvent) {
        if (KeyUtils.isShiftDown()) {
            return;
        }
        String prefixForLength = getPrefix();
        if (prefixForLength.length() != 1) {
            return;
        }
        String prefixForCharacter = getPrefix();
        char prefixCharacter = prefixForCharacter.charAt(0);
        if (prefixCharacter != inputEvent.getCharacter()) {
            return;
        }
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        ChatScreen chatScreen = new ChatScreen("", false);
        minecraft.setScreen(chatScreen);
    }

    private Optional<LiteralArgumentBuilder> createMatchingModuleCommand(Command command) {
        return ClientContext.moduleRegistry.findFirst(module -> {
            String moduleName = module.getName();
            String commandName = command.getName();
            return moduleName.equalsIgnoreCase(commandName);
        }).map(module -> {
            LiteralArgumentBuilder moduleBuilder = LiteralArgumentBuilder.literal("module");
            ModuleCommand moduleCommand = new ModuleCommand(module);
            moduleCommand.exec(moduleBuilder);
            return moduleBuilder;
        });
    }

    public boolean registerCommand(Command command) {
        String commandName = command.getName();
        LiteralArgumentBuilder builder = LiteralArgumentBuilder.literal(commandName);
        command.exec(builder);
        Optional<LiteralArgumentBuilder> moduleCommand = this.createMatchingModuleCommand(command);
        moduleCommand.ifPresent(builder::then);
        LiteralCommandNode canonicalNode = commandDispatcher.register(builder);
        for (String alias : command.getAliases()) {
            CommandAliasBuilder aliasBuilder = new CommandAliasBuilder(alias);
            Iterator children = canonicalNode.getChildren().iterator();
            while (children.hasNext()) {
                CommandNode child = (CommandNode) children.next();
                aliasBuilder.then(child);
            }
            aliasBuilder.executes(canonicalNode.getCommand());
            commandDispatcher.register(aliasBuilder);
        }
        return this.entries.add(command);
    }

    private void registerModuleAliasCommands() {
        Iterator<Module> moduleIterator = ClientContext.moduleRegistry.getRegistry().iterator();
        while (moduleIterator.hasNext()) {
            this.registerModuleAliasCommands(moduleIterator.next());
        }
    }

    /**
     * Registers the automatic {@code ;<alias>} command for one module. Called for every built-in module
     * while this registry is constructed and for addon modules as they are registered later.
     */
    public void registerModuleAliasCommands(Module module) {
        Optional<Command> matchingCommand = this.findFirst(command -> {
            String commandName = command.getName();
            String moduleName = module.getName();
            return commandName.equalsIgnoreCase(moduleName);
        });
        if (matchingCommand.isPresent()) {
            return;
        }
        for (String alias : module.getAliases()) {
            ModuleAliasBuilder aliasBuilder = new ModuleAliasBuilder(alias.toLowerCase());
            ModuleCommand moduleCommand = new ModuleCommand(module);
            moduleCommand.exec(aliasBuilder);
            commandDispatcher.register(aliasBuilder);
        }
    }
}
