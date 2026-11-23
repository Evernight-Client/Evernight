package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Brigadier argument for module categories.
 *
 * <p>{@link EnumArgumentType} cannot be used anymore because {@link ModuleCategory} is a final class
 * rather than an enum. This type resolves names through {@link ModuleCategory#findByName(String)}
 * (case-insensitive, built-in and addon categories alike) and reuses the regular
 * {@code commands.Arguments.UnknownEnum} error message.
 */
public final class ModuleCategoryArgumentType implements ArgumentType<ModuleCategory> {
    private static final String DEFAULT_ARGUMENT_LABEL = "Category";

    private final String argumentLabel;

    public ModuleCategoryArgumentType() {
        this(DEFAULT_ARGUMENT_LABEL);
    }

    public ModuleCategoryArgumentType(String argumentLabel) {
        this.argumentLabel = argumentLabel;
    }

    @Override
    public ModuleCategory parse(StringReader reader) throws CommandSyntaxException {
        String requestedName = reader.readString();
        ModuleCategory category = ModuleCategory.findByName(requestedName);
        if (category != null) {
            return category;
        }
        DynamicCommandExceptionType unknownCategory = new DynamicCommandExceptionType(invalidValue -> {
            Object[] errorArguments = new Object[2];
            errorArguments[0] = this.argumentLabel;
            errorArguments[1] = invalidValue;
            String errorMessage = Translations.format("commands.Arguments.UnknownEnum", errorArguments);
            return Component.literal(errorMessage);
        });
        throw unknownCategory.create(requestedName);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context, SuggestionsBuilder builder) {
        Stream<String> categoryNames = ModuleCategory.values().stream().map(ModuleCategory::getName);
        return SharedSuggestionProvider.suggest(categoryNames, builder);
    }
}
