package evernight.client.commands.control.Prefix;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.Command;

public final class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix");
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        String argumentName = "prefix";
        FancyStringArgument prefixType = FancyStringArgument.fancyString();
        RequiredArgumentBuilder prefixArgument = Command.argument(argumentName, prefixType);
        com.mojang.brigadier.Command<Object> setPrefix = context -> {
            Class<String> prefixClass = String.class;
            String prefix = context.getArgument("prefix", prefixClass);
            CommandRegistry.setPrefix(prefix);
            String messagePrefix = Translations.ui("commands.Prefix.SetPrefix");
            MutableComponent message = Component.literal(messagePrefix);
            String currentPrefix = CommandRegistry.getPrefix();
            MutableComponent formattedPrefix = this.formatText(currentPrefix);
            MutableComponent completeMessage = message.append(formattedPrefix);
            ChatUtils.printChat(completeMessage, -1);
            return 1;
        };
        ArgumentBuilder prefixSubcommand = prefixArgument.executes(setPrefix);
        LiteralArgumentBuilder root = (LiteralArgumentBuilder) builder.then(prefixSubcommand);
        com.mojang.brigadier.Command<Object> showPrefix = context -> {
            String messagePrefix = Translations.ui("commands.Prefix.CurrentPrefix");
            MutableComponent message = Component.literal(messagePrefix);
            String currentPrefix = CommandRegistry.getPrefix();
            MutableComponent formattedPrefix = this.formatText(currentPrefix);
            MutableComponent completeMessage = message.append(formattedPrefix);
            ChatUtils.printChat(completeMessage, -1);
            return 1;
        };
        root.executes(showPrefix);
    }
}