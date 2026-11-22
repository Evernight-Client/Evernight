package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.KitRegistry;
import evernight.client.render.text.Translations;
import evernight.client.utils.inventory.Kit;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import evernight.client.commands.CommandStringReader;

public class KitArgumentType implements ArgumentType {
    @Override
    public Kit parse(StringReader reader) throws CommandSyntaxException {
        String requestedKitName = CommandStringReader.readString(reader);
        KitRegistry kitRegistry = ClientContext.kitRegistry;
        Iterator kits = kitRegistry.getRegistry().iterator();
        while (kits.hasNext()) {
            Kit kit = (Kit) kits.next();
            String kitName = kit.getName();
            if (kitName.equalsIgnoreCase(requestedKitName)) {
                return kit;
            }
        }
        DynamicCommandExceptionType unknownKitException = new DynamicCommandExceptionType(argument -> {
            Object[] formatArguments = new Object[]{argument};
            String errorMessage = Translations.format("commands.Arguments.UnknownKit", formatArguments);
            return Component.literal(errorMessage);
        });
        throw unknownKitException.create(requestedKitName);
    }

    @Override
    public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        KitRegistry kitRegistry = ClientContext.kitRegistry;
        Stream<String> kitNames = kitRegistry.getRegistry().stream().map(Kit::getName);
        return SharedSuggestionProvider.suggest(kitNames, builder);
    }
}