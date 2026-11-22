package evernight.client.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import evernight.client.ClientAccess;
import evernight.client.modules.Module;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.core.Setting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ModuleSettingArgumentType implements ArgumentType, ClientAccess {
    private final Module module;

    public ModuleSettingArgumentType(Module module) {
        this.module = module;
    }

    public static Setting<?> getSetting(CommandContext context, Module module, String argumentName) throws CommandSyntaxException {
        String requestedName = (String) context.getArgument(argumentName, String.class);
        Stream<Setting<?>> settings = module.getSettings().stream().map(setting -> (Setting<?>) setting);
        Stream<Setting<?>> matchingSettings = settings.filter(setting -> {
            String configName = setting.getConfigName();
            return configName.equalsIgnoreCase(requestedName);
        });
        Optional<Setting<?>> matchedSetting = matchingSettings.findFirst();
        if (matchedSetting.isEmpty()) {
            DynamicCommandExceptionType unknownSettingError = new DynamicCommandExceptionType(argument -> {
                String argumentText = String.valueOf(argument);
                String message = Translations.ui("commands.Arguments.SettingNotFoundPrefix").concat(argumentText);
                return Component.literal(message);
            }
            );
            throw unknownSettingError.create(requestedName);
        }
        return matchedSetting.get();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public CompletableFuture listSuggestions(CommandContext context, SuggestionsBuilder builder) {
        Module module = this.module;
        Stream<Setting<?>> settings = module.getSettings().stream().map(setting -> (Setting<?>) setting);
        Stream<String> settingNames = settings.filter(setting -> {
            return !setting.isGroupOnly();
        }).map(JsonConfigurable::getConfigName);
        return SharedSuggestionProvider.suggest(settingNames, builder);
    }
}