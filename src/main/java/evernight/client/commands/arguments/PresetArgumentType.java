package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientContext;
import evernight.client.config.preset.PresetData;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.PresetRegistry;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import evernight.client.commands.CommandStringReader;

public class PresetArgumentType implements ArgumentType {
    public static PresetData getPreset(CommandContext context, String categoryArgumentName, String presetArgumentName) throws CommandSyntaxException {
        PresetCategory category = (PresetCategory) context.getArgument(categoryArgumentName, PresetCategory.class);
        String presetName = (String) context.getArgument(presetArgumentName, String.class);
        ConfigurationManager configurationManager = ClientContext.configurationManager;
        PresetRegistry registryForLookup = configurationManager.getPresetRegistry(category);
        Predicate<PresetData> presetFilter = candidate -> {
            return candidate.getName().equalsIgnoreCase(presetName);
        };
        Optional presetResult = registryForLookup.findFirst(presetFilter);
        if (presetResult.isEmpty()) {
            DynamicCommandExceptionType notFoundException = new DynamicCommandExceptionType(missingName -> {
                String missingNameText = String.valueOf(missingName);
                String errorMessage = Translations.ui("commands.Arguments.PresetNotFoundPrefix") + missingNameText;
                return Component.nullToEmpty(errorMessage);
            }
            );
            throw notFoundException.create(presetName);
        } else {
            return (PresetData) presetResult.get();
        }
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return CommandStringReader.readString(reader);
    }

    @Override
    public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        Supplier<Suggestions> suggestionsSupplier = () -> {
            ConfigurationManager configurationManager = ClientContext.configurationManager;
            PresetCategory category = (PresetCategory) context.getArgument("category", PresetCategory.class);
            PresetRegistry presetRegistry = configurationManager.getPresetRegistry(category);
            try {
                presetRegistry.loadPresets();
            } catch (Throwable ignoredReloadFailure) {
            }
            Stream<String> presetNames = presetRegistry.getRegistry().stream().map(PresetData::getName);
            try {
                CompletableFuture suggestionsFuture = SharedSuggestionProvider.suggest(presetNames, builder);
                return (Suggestions) suggestionsFuture.get();
            } catch (Exception ignoredSuggestionFailure) {
                return null;
            }
        };
        return CompletableFuture.supplyAsync(suggestionsSupplier);
    }
}