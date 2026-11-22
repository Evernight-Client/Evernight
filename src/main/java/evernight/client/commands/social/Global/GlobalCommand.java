package evernight.client.commands.social.Global;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import evernight.client.commands.Command;

public final class GlobalCommand extends Command {
    public GlobalCommand() {
        super("global");
        String[] aliases = {"syncall"};
        setAliases(aliases);
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        LiteralArgumentBuilder colorCommand = Command.literal("color");
        ArgumentType colorType = BoolArgumentType.bool();
        RequiredArgumentBuilder colorValue = Command.argument("value", colorType);
        com.mojang.brigadier.Command<Object> colorAction = context -> {
            boolean synced = context.getArgument("value", Boolean.class);
            BiConsumer<Module, Setting<?>> syncColor = (module, setting) -> {
                if (!(module instanceof ColorsModule) && !(module instanceof UIModule)) {
                    if (setting instanceof ColorSetting color) {
                        color.setSynced(synced);
                    }
                }
            };
            forEachSetting(syncColor);
            ChatUtils.printChat(Component.literal(Translations.ui(synced ? "commands.Global.ColorSyncEnabled" : "commands.Global.ColorSyncDisabled")), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        };
        ArgumentBuilder colorArguments = colorValue.executes(colorAction);
        ArgumentBuilder colorSubcommand = colorCommand.then(colorArguments);
        builder.then(colorSubcommand);
        LiteralArgumentBuilder lineWidthCommand = Command.literal("linewidth");
        ArgumentType lineWidthType = FloatArgumentType.floatArg(0.1F, 5.0F);
        RequiredArgumentBuilder lineWidthValue = Command.argument("value", lineWidthType);
        com.mojang.brigadier.Command<Object> lineWidthAction = context -> {
            Float argument = context.getArgument("value", Float.class);
            float lineWidth = argument;
            BiConsumer<Module, Setting<?>> setLineWidth = (module, setting) -> {
                if (setting.getValue() instanceof Float) {
                    String name = setting.getName();
                    if (name.equalsIgnoreCase("LineWidth")) {
                        NumberSetting<?> number = (NumberSetting<?>) setting;
                        Float value = lineWidth;
                        number.setNumber(value);
                    }
                }
            };
            forEachSetting(setLineWidth);
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Global.LineWidthPrefix")).append(this.formatText(String.valueOf(lineWidth))).append(Translations.ui("commands.Global.LineWidthSuffix")), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        };
        ArgumentBuilder lineWidthArguments = lineWidthValue.executes(lineWidthAction);
        ArgumentBuilder lineWidthSubcommand = lineWidthCommand.then(lineWidthArguments);
        builder.then(lineWidthSubcommand);
    }

    private void forEachSetting(BiConsumer<Module, Setting<?>> action) {
        ModuleRegistry registry = ClientContext.moduleRegistry;
        List modules = registry.getRegistry();
        Iterator moduleIterator = modules.iterator();
        while (moduleIterator.hasNext()) {
            Module module = (Module) moduleIterator.next();
            List settings = module.getSettings();
            Iterator settingIterator = settings.iterator();
            while (settingIterator.hasNext()) {
                Setting<?> setting = (Setting<?>) settingIterator.next();
                action.accept(module, setting);
            }
        }
    }
}