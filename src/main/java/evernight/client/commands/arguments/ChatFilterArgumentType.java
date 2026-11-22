package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ChatFilterRegistry;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatFilterEntry;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import evernight.client.commands.CommandStringReader;

public class ChatFilterArgumentType implements ArgumentType<ChatFilterEntry> {
    @Override
    public ChatFilterEntry parse(StringReader reader) throws CommandSyntaxException {
        String filterId = CommandStringReader.readString(reader);
        ChatFilterRegistry filterRegistry = ClientContext.chatFilterRegistry;
        Optional<ChatFilterEntry> matchingFilter = filterRegistry.findById(filterId);
        if (matchingFilter.isEmpty()) {
            DynamicCommandExceptionType unknownFilter = new DynamicCommandExceptionType(argument -> {
                String argumentText = String.valueOf(argument);
                String errorMessage = Translations.ui("commands.Arguments.FilterNotFoundPrefix").concat(argumentText);
                return Component.nullToEmpty(errorMessage);
            }
            );
            throw unknownFilter.create(filterId);
        }
        return matchingFilter.get();
    }

    @Override
    public CompletableFuture<Suggestions> listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        ChatFilterRegistry filterRegistry = ClientContext.chatFilterRegistry;
        Stream<String> filterIds = filterRegistry.streamIds();
        return SharedSuggestionProvider.suggest(filterIds, builder);
    }
}