package evernight.client.commands.world.Waypoints;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.WaypointRegistry;
import evernight.client.modules.render.Waypoints.WaypointsModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.DimensionType;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.world.Waypoint;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;
import evernight.client.commands.arguments.EnumArgumentType;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.arguments.PositionArgument;
import evernight.client.commands.movement.Elytra.ElytraCommand;
import evernight.client.commands.movement.WaypointWalk.WaypointWalkCommand;
import evernight.client.utils.core.WaypointServerKey;
import evernight.client.commands.Command;

public final class WaypointsCommand extends Command {
    public WaypointsCommand() {
        super("waypoints");
        String[] aliases = new String[1];
        aliases[0] = "wp";
        this.setAliases(aliases);
    }

    public static String getCurrentServerKey() {
        return evernight.client.utils.core.WaypointServerKey.getCurrent();
    }

    public static Vec3 resolveWaypointPosition(CommandContext context) {
        String name = (String) context.getArgument("name", String.class);
        Optional waypointResult = ClientContext.waypointRegistry.findFirst(candidate -> {
            String server = getCurrentServerKey();
            return candidate.matches(name, server);
        });
        Waypoint waypoint = (Waypoint) waypointResult.orElse(null);
        if (waypoint == null) {
            return null;
        }
        return WaypointsModule.getPositionInCurrentDimension(waypoint);
    }

    public static CompletableFuture suggestNamesForServer(
            CommandContext context, SuggestionsBuilder builder, String server) {
        WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
        Stream<String> names = waypointRegistry.getRegistry().stream().filter(candidate -> {
            String candidateServerSnapshot = candidate.getServer();
            return candidateServerSnapshot.equalsIgnoreCase(server);
        }).map(Waypoint::getName);
        return SharedSuggestionProvider.suggest(names, builder);
    }

    static String formatServerSuggestion(Waypoint waypoint) {
        String server = waypoint.getServer();
        String lowercaseServer = server.toLowerCase();
        if (lowercaseServer.contains(":") || lowercaseServer.contains(" ")) {
            return "\"" + lowercaseServer + "\"";
        } else {
            return lowercaseServer;
        }
    }

    static CompletableFuture suggestServers(CommandContext context, SuggestionsBuilder builder) {
        WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
        Stream<String> servers = waypointRegistry.getRegistry().stream()
                .map(WaypointsCommand::formatServerSuggestion).distinct();
        return SharedSuggestionProvider.suggest(servers, builder);
    }

    static CompletableFuture suggestWaypointNames(CommandContext context, SuggestionsBuilder builder) {
        String server;
        try {
            server = (String) context.getArgument("server", String.class);
        } catch (IllegalArgumentException missingArgument) {
            server = getCurrentServerKey();
        }
        return suggestNamesForServer(context, builder, server);
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        LiteralArgumentBuilder operationBuilder = Command.literal("add");
        RequiredArgumentBuilder operationArgument = Command.argument("name", FancyStringArgument.fancyString());
        RequiredArgumentBuilder positionBranch = (RequiredArgumentBuilder) (Command.argument("pos", new PositionArgument())).executes((com.mojang.brigadier.Command<Object>) context -> {
            String name = context.getArgument("name", String.class);
            Vec3 position = context.getArgument("pos", Vec3.class);
            DimensionType currentDimension = WorldUtils.getDimension();
            String dimensionKey = currentDimension.getWaypointKey();
            Waypoint waypoint = new Waypoint(name, position, dimensionKey, getCurrentServerKey());
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.registerWaypoint(waypoint);
            String messagePrefix = Translations.ui("commands.Waypoints.CreatedPrefix");
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithWaypoint = message.append(waypoint.getDisplayComponent());
            String messageSuffix = Translations.ui("commands.Waypoints.CreatedSuffix");
            MutableComponent messageForSuffix = messageWithWaypoint;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
        });
        ArgumentBuilder dimensionBranch = (Command.argument("dimension", new EnumArgumentType(DimensionType.class, "dimension"))).executes((com.mojang.brigadier.Command<Object>) context -> {
            String name = context.getArgument("name", String.class);
            Vec3 position = context.getArgument("pos", Vec3.class);
            DimensionType dimension = context.getArgument("dimension", DimensionType.class);
            String dimensionKey = dimension.getWaypointKey();
            Waypoint waypoint = new Waypoint(name, position, dimensionKey, getCurrentServerKey());
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.registerWaypoint(waypoint);
            String messagePrefix = Translations.ui("commands.Waypoints.CreatedPrefix");
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithWaypoint = message.append(waypoint.getDisplayComponent());
            String messageSuffix = Translations.ui("commands.Waypoints.CreatedSuffix");
            MutableComponent messageForSuffix = messageWithWaypoint;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
        });
        ArgumentBuilder positionWithDimension = positionBranch.then(dimensionBranch);
        ArgumentBuilder addBranch = operationArgument.then(positionWithDimension).executes((com.mojang.brigadier.Command<Object>) context -> {
            String name = context.getArgument("name", String.class);
            DimensionType currentDimension = WorldUtils.getDimension();
            String dimensionKey = currentDimension.getWaypointKey();
            LocalPlayer playerForPosition = ClientAccess.evernightMinecraft.player;
            Vec3 playerPosition = playerForPosition.position();
            Waypoint waypoint = new Waypoint(name, playerPosition, dimensionKey, getCurrentServerKey());
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.registerWaypoint(waypoint);
            String messagePrefix = Translations.ui("commands.Waypoints.CreatedPrefix");
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithWaypoint = message.append(waypoint.getDisplayComponent());
            String messageSuffix = Translations.ui("commands.Waypoints.CreatedSuffix");
            MutableComponent messageForSuffix = messageWithWaypoint;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
        });
        ArgumentBuilder addTree = operationBuilder.then(addBranch);
        LiteralArgumentBuilder rootWithCommands = (LiteralArgumentBuilder) root.then(addTree);
        String[] removeAliasNames = new String[3];
        removeAliasNames[0] = "remove";
        removeAliasNames[1] = "delete";
        removeAliasNames[(2)] = "del";
        RequiredArgumentBuilder removeAliasBuilder = Command.aliasedLiteral(removeAliasNames);
        operationArgument = (Command.argument("server", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) WaypointsCommand::suggestServers);
        ArgumentBuilder removeNameBranch = ((Command.argument("name", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) WaypointsCommand::suggestWaypointNames)).executes((com.mojang.brigadier.Command<Object>) context -> {
            String name = context.getArgument("name", String.class);
            String server = context.getArgument("server", String.class);
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            waypointRegistry.getRegistry().removeIf(candidate -> {
                String candidateServer = candidate.getServer();
                if (server.equalsIgnoreCase(candidateServer)) {
                    String candidateName = candidate.getName();
                    return name.equalsIgnoreCase(candidateName);
                }
                return false;
            });
            String messagePrefix = Translations.ui("commands.Waypoints.RemovedPrefix");
            MutableComponent message = Component.literal(messagePrefix);
            MutableComponent messageWithName = message.append(name);
            String messageSuffix = Translations.ui("commands.Waypoints.RemovedSuffix");
            MutableComponent messageForSuffix = messageWithName;
            ChatUtils.printChat(messageForSuffix.append(messageSuffix), -1);
            return 1;
        });
        ArgumentBuilder removeServerTree = operationArgument.then(removeNameBranch);
        ArgumentBuilder removeTree = removeAliasBuilder.then(removeServerTree);
        rootWithCommands = (LiteralArgumentBuilder) rootWithCommands.then(removeTree);
        operationBuilder = Command.literal("rename");
        operationArgument = (Command.argument("server", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) WaypointsCommand::suggestServers);
        RequiredArgumentBuilder renameNameBuilder = (Command.argument("name", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) WaypointsCommand::suggestWaypointNames);
        ArgumentBuilder renameTargetBranch = (Command.argument("target", StringArgumentType.string())).executes((com.mojang.brigadier.Command<Object>) context -> {
            String name = context.getArgument("name", String.class);
            String server = context.getArgument("server", String.class);
            String targetName = context.getArgument("target", String.class);
            Predicate<Waypoint> matchesWaypoint = candidate -> {
                String candidateServer = candidate.getServer();
                if (server.equalsIgnoreCase(candidateServer)) {
                    String candidateName = candidate.getName();
                    return name.equalsIgnoreCase(candidateName);
                }
                return false;
            };
            Waypoint waypoint = ClientContext.waypointRegistry.findFirst(matchesWaypoint).orElse(null);
            if (waypoint == null) {
                String missingWaypointMessage = Translations.ui("commands.Waypoints.NotFound");
                ChatUtils.printChat(Component.nullToEmpty(missingWaypointMessage), -1);
                return 1;
            } else {
                WaypointRegistry registryForRemoval = ClientContext.waypointRegistry;
                registryForRemoval.getRegistry().removeIf(matchesWaypoint);
                WaypointRegistry registryForReplacement = ClientContext.waypointRegistry;
                List replacementRegistry = registryForReplacement.getRegistry();
                Vec3 waypointPosition = waypoint.getPosition();
                String waypointDimension = waypoint.getDimension();
                replacementRegistry.add(new Waypoint(targetName, waypointPosition, waypointDimension,
                        waypoint.getServer()));
                String messagePrefix = Translations.ui("commands.Waypoints.RenamedPrefix");
                MutableComponent renameMessage = Component.literal(messagePrefix);
                renameMessage = renameMessage.append(name);
                String messageSuffix = Translations.ui("commands.Waypoints.RenamedMiddle");
                MutableComponent messageForSuffix = renameMessage;
                renameMessage = messageForSuffix.append(messageSuffix);
                renameMessage = renameMessage.append(targetName);
                ChatUtils.printChat(renameMessage, -1);
                return 1;
            }
        });
        ArgumentBuilder renameNameTree = renameNameBuilder.then(renameTargetBranch);
        ArgumentBuilder renameServerTree = operationArgument.then(renameNameTree);
        ArgumentBuilder renameTree = operationBuilder.then(renameServerTree);
        rootWithCommands = (LiteralArgumentBuilder) rootWithCommands.then(renameTree);
        ArgumentBuilder listBranch = (Command.literal("list")).executes((com.mojang.brigadier.Command<Object>) context -> {
            WaypointRegistry waypointRegistry = ClientContext.waypointRegistry;
            List<Component> waypointComponents = waypointRegistry.getRegistry().stream()
                    .sorted(Comparator.comparing(Waypoint::isToggled).reversed())
                    .map(Waypoint::getDisplayComponent).toList();
            MutableComponent message = Component.empty();
            String messagePrefix = Translations.ui("commands.Waypoints.ListHeader");
            message.append(messagePrefix);
            message.append(ComponentUtils.formatList(waypointComponents, Component.literal(Translations.ui("commands.Waypoints.ListSeparator"))));
            ChatUtils.printChat(message, -1);
            return 1;
        });
        rootWithCommands.then(listBranch);
        operationBuilder = Command.literal("toggle");
        ArgumentBuilder toggleNameBranch = ((Command.argument("name", StringArgumentType.string())).suggests((com.mojang.brigadier.suggestion.SuggestionProvider<Object>) WaypointsCommand::suggestWaypointNames)).executes((com.mojang.brigadier.Command<Object>) context -> {
            String name = context.getArgument("name", String.class);
            String server = getCurrentServerKey();
            Predicate<Waypoint> matchesWaypoint = candidate -> {
                String candidateServer = candidate.getServer();
                if (server.equalsIgnoreCase(candidateServer)) {
                    String candidateName = candidate.getName();
                    return name.equalsIgnoreCase(candidateName);
                }
                return false;
            };
            Waypoint waypoint = ClientContext.waypointRegistry.findFirst(matchesWaypoint).orElse(null);
            if (waypoint == null) {
                String missingWaypointMessage = Translations.ui("commands.Waypoints.NotFound");
                ChatUtils.printChat(Component.nullToEmpty(missingWaypointMessage), -1);
                return 1;
            }
            boolean newVisibility = !waypoint.isToggled();
            waypoint.setToggled(newVisibility);
            Object[] formatArguments = new Object[2];
            formatArguments[0] = name;
            String visibilityName;
            if (waypoint.isToggled()) {
                visibilityName = Translations.ui("commands.Waypoints.Visible");
            } else {
                visibilityName = Translations.ui("commands.Waypoints.Invisible");
            }
            formatArguments[1] = visibilityName;
            String toggleMessage = Translations.format("commands.Waypoints.Toggle", formatArguments);
            ChatUtils.printChat(Component.literal(toggleMessage), -1);
            return 1;
        });
        ArgumentBuilder toggleTree = operationBuilder.then(toggleNameBranch);
        root.then(toggleTree);
        if ((ClientContext.baritoneBridge).isAvailable()) {
            ElytraCommand elytraCommand = new ElytraCommand();
            this.addSubcommand(elytraCommand, root);
            WaypointWalkCommand walkCommand = new WaypointWalkCommand();
            this.addSubcommand(walkCommand, root);
        }
    }
}