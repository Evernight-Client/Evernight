package evernight.client.commands.misc.Session;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import evernight.mixin.minecraft.client.UserAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.Command;

public final class SessionCommand extends Command {
    public SessionCommand() {
        super("session");
    }

    private String createRandomName() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        FancyStringArgument nameArgument = FancyStringArgument.fancyString();
        RequiredArgumentBuilder nameBuilder = Command.argument("name", nameArgument);
        com.mojang.brigadier.Command<Object> namedCommand = context -> {
            String name = context.getArgument("name", String.class);
            setSessionName(name);
            return 1;
        };
        ArgumentBuilder nameCommandBuilder = nameBuilder.executes(namedCommand);
        builder.then(nameCommandBuilder);
        com.mojang.brigadier.Command<Object> randomNameCommand = context -> {
            String randomName = createRandomName();
            setSessionName(randomName);
            return 1;
        };
        builder.executes(randomNameCommand);
    }

    private void setSessionName(String name) {
        if (name.isEmpty() || name.length() > 16) {
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Session.InvalidName")), -1);
            return;
        }
        Minecraft client = ClientAccess.evernightMinecraft;
        ClientPacketListener listener = client.getConnection();
        ServerData server = listener == null ? null : listener.getServerData();
        UserAccessor user = (UserAccessor) client.getUser();
        user.setUsername(name);
        user.setUuid(UUID.randomUUID());
        ChatUtils.printChat(Component.literal(Translations.format("commands.Session.Set", name)), -1);
        if (server != null) {
            listener.getConnection().disconnect(Component.empty());
            ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client,
                    ServerAddress.parseString(server.ip), server, true, null);
        }
    }
}