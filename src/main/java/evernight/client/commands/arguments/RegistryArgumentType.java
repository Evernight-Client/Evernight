package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.managers.registry.Registry;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class RegistryArgumentType implements ArgumentType<NamedEntry> {
    private final Registry<?> registry;

    public RegistryArgumentType(Registry<?> registry) {
        this.registry = registry;
    }

    public static RegistryArgumentType create(Registry<?> registry) {
        return new RegistryArgumentType(registry);
    }

    @Override
    public NamedEntry parse(StringReader reader) throws CommandSyntaxException {
        String entryName = reader.readString();
        Registry<?> registrySnapshot = this.registry;
        List<? extends NamedEntry> registryEntries = registrySnapshot.getRegistry();
        Iterator<? extends NamedEntry> entries = registryEntries.iterator();
        while (entries.hasNext()) {
            NamedEntry entry = entries.next();
            String entryNameValue = entry.getName();
            if (entryNameValue.equalsIgnoreCase(entryName)) {
                return entry;
            }
        }
        DynamicCommandExceptionType createdExceptionType = new DynamicCommandExceptionType(invalidName -> {
            Object[] formatArguments = new Object[]{invalidName};
            String message = Translations.format("commands.Arguments.UnknownEntry", formatArguments);
            return Component.literal(message);
        });
        throw createdExceptionType.create(entryName);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        Registry<?> registrySnapshot = this.registry;
        List<? extends NamedEntry> entries = registrySnapshot.getRegistry();
        Stream<String> names = entries.stream().map(NamedEntry::getName);
        return SharedSuggestionProvider.suggest(names, builder);
    }
}