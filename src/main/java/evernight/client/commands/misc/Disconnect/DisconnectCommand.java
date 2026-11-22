package evernight.client.commands.misc.Disconnect;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.modules.exploit.IllegalDisconnect.IllegalDisconnectModule;
import evernight.client.render.text.Translations;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import evernight.client.commands.Command;

public final class DisconnectCommand extends Command {
    public DisconnectCommand() {
        super("disconnect");
        String[] aliases = {"dc"};
        this.setAliases(aliases);
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        LiteralArgumentBuilder illegal = Command.literal("illegal");
        com.mojang.brigadier.Command<Object> disconnectIllegally = context -> {
            if (!this.isWorldUnavailable()) {
                IllegalDisconnectModule.sendInvalidChatPacket();
            }
            return 1;
        };
        ArgumentBuilder illegalCommand = illegal.executes(disconnectIllegally);
        LiteralArgumentBuilder commandWithIllegal = (LiteralArgumentBuilder) builder.then(illegalCommand);
        com.mojang.brigadier.Command<Object> disconnect = context -> {
            if (!this.isWorldUnavailable()) {
                ClientPacketListener listener = ClientAccess.evernightMinecraft.player.connection;
                Connection connection = listener.getConnection();
                Component reason = Component.nullToEmpty(Translations.ui("commands.Disconnect.Reason"));
                connection.disconnect(reason);
            }
            return 1;
        };
        commandWithIllegal.executes(disconnect);
    }
}