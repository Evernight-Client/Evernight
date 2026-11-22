package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.PresetRegistry;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class PresetCategoryArgument implements ArgumentType {
    public static PresetRegistry getManager(CommandContext context, String argumentName) {
        PresetCategory category = (PresetCategory) context.getArgument(argumentName, PresetCategory.class);
        ConfigurationManager presetManagers = ClientContext.configurationManager;
        return presetManagers.getPresetRegistry(category);
    }

    public PresetCategory parseCategory(StringReader reader) throws CommandSyntaxException {
        String categoryName = reader.readString();
        PresetCategory category = PresetCategory.findByName(categoryName);
        if (category == null) {
            DynamicCommandExceptionType createdExceptionType = new DynamicCommandExceptionType(invalidName -> {
                String invalidNameText = String.valueOf(invalidName);
                String message = Translations.ui("commands.Arguments.PresetCategoryNotFoundPrefix").concat(invalidNameText);
                return Component.nullToEmpty(message);
            }
            );
            throw createdExceptionType.create(categoryName);
        } else {
            return category;
        }
    }

    @Override
    public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        PresetCategory[] categories = PresetCategory.values();
        Stream<String> categoryNames = Arrays.stream(categories).map(category -> {
            String categoryName = category.getName();
            Locale locale = Locale.ROOT;
            return categoryName.toLowerCase(locale);
        });
        return SharedSuggestionProvider.suggest(categoryNames, builder);
    }

    @Override
    public Object parse(StringReader reader) throws CommandSyntaxException {
        return this.parseCategory(reader);
    }
}