package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.NamedFeature;
import evernight.client.managers.registry.MacroRegistry;
import evernight.client.modules.misc.Macro;
import evernight.client.render.text.Translations;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

public class MacroArgumentType implements ArgumentType {
    @Override
    public Macro parse(StringReader reader) throws CommandSyntaxException {
        String requestedName = reader.readString();
        MacroRegistry macroRegistry = ClientContext.macroRegistry;
        Iterator macros = macroRegistry.getRegistry().iterator();
        while (macros.hasNext()) {
            Macro macro = (Macro) macros.next();
            String macroName = macro.getName();
            if (macroName.equalsIgnoreCase(requestedName)) {
                return macro;
            }
        }
        DynamicCommandExceptionType unknownMacroException = new DynamicCommandExceptionType(invalidName -> {
            Object[] formatArguments = new Object[]{invalidName};
            String errorMessage = Translations.format("commands.Arguments.UnknownMacro", formatArguments);
            return Component.literal(errorMessage);
        });
        throw unknownMacroException.create(requestedName);
    }

    @Override
    public CompletableFuture listSuggestions(CommandContext unusedContext, SuggestionsBuilder suggestions) {
        MacroRegistry macroRegistry = ClientContext.macroRegistry;
        Stream names = macroRegistry.getRegistry().stream().map(NamedFeature::getName);
        return SharedSuggestionProvider.suggest(names, suggestions);
    }
}