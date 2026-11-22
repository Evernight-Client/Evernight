package evernight.client.commands.misc.Help;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.modules.Keybind;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import evernight.client.commands.Command;

public final class HelpCommand extends Command {
    public HelpCommand() {
        super("help");
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        File clientDirectory = new File("evernight");
        if (!clientDirectory.exists()) {
            TaskScheduler scheduler = ClientContext.taskScheduler;
            Runnable welcomeAction = () -> {
                MutableComponent message = Component.literal(Translations.ui("commands.Help.Welcome"));
                MessageSignature welcomeSignature = ChatUtils.createSignature(-1001);
                ChatUtils.printChat(message, welcomeSignature);
                message = Component.literal(Translations.ui("commands.Help.UiBindPrefix"));
                UIModule uiModule = UIModule.INSTANCE;
                Keybind uiKeybind = uiModule.getKeybind();
                String keybindName = uiKeybind.getDisplayName();
                HelpCommand commandForKeybind = this;
                MutableComponent formattedKeybind = commandForKeybind.formatText(keybindName);
                message = message.append(formattedKeybind);
                MessageSignature uiSignature = ChatUtils.createSignature(-1002);
                ChatUtils.printChat(message, uiSignature);
                message = Component.literal(Translations.ui("commands.Help.CommandPrefixPrefix"));
                String commandPrefix = CommandRegistry.getPrefix();
                HelpCommand commandForPrefix = this;
                MutableComponent formattedPrefix = commandForPrefix.formatText(commandPrefix);
                message = message.append(formattedPrefix);
                MessageSignature prefixSignature = ChatUtils.createSignature(-1003);
                ChatUtils.printChat(message, prefixSignature);
            };
            scheduler.scheduleRotationTask(welcomeAction, 0);
        }
        com.mojang.brigadier.Command<Object> helpAction = context -> {
            ArrayList<String> commandNames = new ArrayList<>();
            CommandRegistry registry = ClientContext.commandRegistry;
            List<Command> commands = registry.getRegistry();
            commands.forEach(command -> {
                commandNames.add(command.getName());
            });
            MutableComponent message = Component.empty();
            Object[] countArguments = new Object[1];
            int count = commandNames.size();
            countArguments[0] = count;
            String heading = Translations.format("commands.Help.CommandsHeader", countArguments);
            message.append(heading);
            String formattedNames = String.join(", ", commandNames) + ".";
            Component namesComponent = Component.nullToEmpty(formattedNames);
            message.append(namesComponent);
            MessageSignature helpSignature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(message, helpSignature);
            return 1;
        };
        root.executes(helpAction);
    }
}