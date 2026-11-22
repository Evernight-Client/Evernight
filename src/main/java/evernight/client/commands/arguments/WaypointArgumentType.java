package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.WaypointRegistry;
import evernight.client.render.text.Translations;
import evernight.client.utils.world.Waypoint;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class WaypointArgumentType implements ArgumentType<Waypoint> {
    private static final String unknownWaypointFormat;

    static {
        unknownWaypointFormat = "Waypoint %s doesn't exists";
    }

    @Override
    public Waypoint parse(StringReader reader) throws CommandSyntaxException {
        String requestedName = reader.readString();
        WaypointRegistry registryForParsing = ClientContext.waypointRegistry;
        Iterator<Waypoint> waypointIterator = registryForParsing.getRegistry().iterator();
        while (waypointIterator.hasNext()) {
            Waypoint waypoint = waypointIterator.next();
            if (waypoint != null && waypoint.getName() != null) {
                String waypointName = waypoint.getName();
                if (waypointName.equalsIgnoreCase(requestedName)) {
                    return waypoint;
                }
            }
        }
        DynamicCommandExceptionType unknownWaypointError = new DynamicCommandExceptionType(argument -> {
            String errorFormat = unknownWaypointFormat;
            Object[] formatArguments = new Object[]{argument};
            String message = Translations.format("commands.Arguments.UnknownWaypoint", formatArguments);
            return Component.literal(message);
        });
        throw unknownWaypointError.create(requestedName);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context, SuggestionsBuilder builder) {
        ArrayList<Waypoint> invalidWaypoints = new ArrayList<>();
        WaypointRegistry registryForSuggestionScan = ClientContext.waypointRegistry;
        Iterator<Waypoint> waypointIterator = registryForSuggestionScan.getRegistry().iterator();
        while (waypointIterator.hasNext()) {
            Waypoint waypoint = waypointIterator.next();
            if (waypoint != null && (waypoint.getServer() == null || waypoint.getDimension() == null || waypoint.getName() == null)) {
                invalidWaypoints.add(waypoint);
            }
        }
        waypointIterator = invalidWaypoints.iterator();
        while (waypointIterator.hasNext()) {
            Waypoint invalidWaypoint = waypointIterator.next();
            ClientContext.waypointRegistry.unregisterWaypoint(invalidWaypoint);
        }
        WaypointRegistry suggestionRegistry = ClientContext.waypointRegistry;
        Stream<String> waypointNames = suggestionRegistry.getRegistry().stream().map(Waypoint::getName);
        return SharedSuggestionProvider.suggest(waypointNames, builder);
    }
}