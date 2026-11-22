package evernight.client.commands.social.Say;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.commands.Command;

public final class SayCommand extends Command {
    public SayCommand() {
        super("say");
    }

    @Override
    public void exec(LiteralArgumentBuilder commandBuilder) {
        StringArgumentType messageArgumentType = StringArgumentType.greedyString();
        RequiredArgumentBuilder messageArgument = Command.argument("message", messageArgumentType);
        com.mojang.brigadier.Command<Object> sendMessage = context -> {
            Class<String> messageType = String.class;
            String messageKey = "message";
            CommandContext messageContext = context;
            String message = (String) (messageContext.getArgument(messageKey, messageType));
            ChatUtils.sendChatOrCommand(message);
            return 1;
        };
        ArgumentBuilder messageBranch = messageArgument.executes(sendMessage);
        commandBuilder.then(messageBranch);
    }
}