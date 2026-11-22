package evernight.client.commands.movement.Yaw;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import evernight.client.commands.arguments.RelativeFloatArgument;
import evernight.client.commands.Command;

public final class YawCommand extends Command {
    public YawCommand() {
        super("yaw");
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        String rotationArgumentName = "yaw";
        RelativeFloatArgument argumentType = RelativeFloatArgument.relativeFloatArg();
        RequiredArgumentBuilder argumentBuilder = Command.argument(rotationArgumentName, argumentType);
        com.mojang.brigadier.Command<Object> rotationAction = commandContext -> {
            RelativeFloatArgument.RelativeFloat argument = commandContext.getArgument("yaw", RelativeFloatArgument.RelativeFloat.class);
            float yaw = argument.value();
            if (argument.relative()) {
                LocalPlayer playerForRelativeYaw = ClientAccess.evernightMinecraft.player;
                yaw += playerForRelativeYaw.getYRot();
            }
            if (Float.isNaN(yaw)) {
                LocalPlayer playerForNaNYaw = ClientAccess.evernightMinecraft.player;
                yaw = playerForNaNYaw.getYRot();
            }
            float yawMagnitude = Math.abs(yaw);
            if (yawMagnitude > 7.2e+03F) {
                yaw %= 3.6e+02F;
            }
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            player.setYRot(yaw);
            String suffix = yaw > 180.0F || yaw < -180.0F ? " (%s)".formatted(yaw) : "";
            float wrappedYaw = Mth.wrapDegrees(yaw);
            String formattedMessage = Translations.format("commands.Yaw.Set", wrappedYaw, suffix);
            Component message = Component.literal(formattedMessage);
            ChatUtils.printChat(message, -1);
            return 1;
        };
        ArgumentBuilder rotation = argumentBuilder.executes(rotationAction);
        builder.then(rotation);
    }
}