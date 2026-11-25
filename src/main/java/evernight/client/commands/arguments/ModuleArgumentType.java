package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.NamedFeature;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.render.text.Translations;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ModuleArgumentType implements ArgumentType {
    private static final DynamicCommandExceptionType UNKNOWN_MODULE;

    static {
        UNKNOWN_MODULE = new DynamicCommandExceptionType(argument ->
                Component.nullToEmpty(Translations.ui("commands.Arguments.ModuleNotFoundPrefix") + argument));
    }

    @Override
    public Module parse(StringReader reader) throws CommandSyntaxException {
        String moduleName = reader.readString();
        ModuleRegistry registry = ClientContext.moduleRegistry;
        Predicate<Module> moduleMatches = module -> {
            for (String alias : module.getAliases()) {
                String requestedName = moduleName;
                String capturedAlias = alias;
                if (capturedAlias.equalsIgnoreCase(requestedName)) {
                    return true;
                }
            }
            return false;
        };
        Optional matchingModule = registry.findFirst(moduleMatches);
        if (matchingModule.isEmpty()) {
            String unmatchedName = moduleName;
            DynamicCommandExceptionType exceptionType = UNKNOWN_MODULE;
            throw exceptionType.create(unmatchedName);
        } else {
            return (Module) matchingModule.get();
        }
    }

    @Override
    public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        ModuleRegistry modules = ClientContext.moduleRegistry;
        Stream<String> moduleNames = modules.getRegistry().stream().filter(Module::isAvailable).map(NamedFeature::getName);
        return SharedSuggestionProvider.suggest(moduleNames, builder);
    }
}