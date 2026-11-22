package evernight.client.commands.movement.Pitch;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.math.MathConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import evernight.client.commands.arguments.RelativeFloatArgument;
import evernight.client.commands.Command;

public final class PitchCommand extends Command {
    public PitchCommand() {
        super("pitch");
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        String rotationArgumentName = "pitch";
        RelativeFloatArgument argumentType = RelativeFloatArgument.relativeFloatArg();
        RequiredArgumentBuilder argumentBuilder = Command.argument(rotationArgumentName, argumentType);
        com.mojang.brigadier.Command<Object> rotationAction = commandContext -> {
            RelativeFloatArgument.RelativeFloat argument = commandContext.getArgument("pitch", RelativeFloatArgument.RelativeFloat.class);
            float pitch = argument.value();
            if (argument.relative()) {
                LocalPlayer playerForPitch = ClientAccess.evernightMinecraft.player;
                pitch += playerForPitch.getXRot();
            }
            pitch = Mth.clamp(pitch, -MathConstants.NINETY_DEGREES, MathConstants.NINETY_DEGREES);
            float appliedPitch = pitch;
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            player.setXRot(appliedPitch);
            String messageText = Translations.format("commands.Pitch.Set", pitch);
            MutableComponent message = Component.literal(messageText);
            MessageSignature signature = ChatUtils.createSignature(-1);
            ChatUtils.printChat(message, signature);
            return 1;
        };
        ArgumentBuilder rotation = argumentBuilder.executes(rotationAction);
        builder.then(rotation);
    }
}