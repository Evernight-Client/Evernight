package evernight.client.commands.world.Coords;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.DimensionType;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import evernight.client.commands.arguments.PlayerNameArgumentType;
import evernight.client.commands.Command;

public final class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords");
    }

    private String formatCoordinates() {
        if (ClientAccess.evernightMinecraft.player == null) {
            return "";
        }
        LocalPlayer playerForX = ClientAccess.evernightMinecraft.player;
        int x = (int) playerForX.getX();
        LocalPlayer playerForY = ClientAccess.evernightMinecraft.player;
        int y = (int) playerForY.getY();
        LocalPlayer playerForZ = ClientAccess.evernightMinecraft.player;
        int z = (int) playerForZ.getZ();
        DimensionType dimension = WorldUtils.getDimension();
        String dimensionName = dimension.getDisplayName();
        double randomValue = Math.random();
        double scaledRandom = randomValue * 10000.0D;
        int randomSuffix = (int) Math.floor(scaledRandom);
        return Translations.format("commands.Coords.Format", x, y, z, dimensionName, randomSuffix);
    }

    @Override
    public void exec(LiteralArgumentBuilder root) {
        PlayerNameArgumentType playerNameType = new PlayerNameArgumentType();
        RequiredArgumentBuilder nameBuilder = Command.argument("name", playerNameType);
        com.mojang.brigadier.Command<Object> whisperAction = context -> {
            String playerName = context.getArgument("name", String.class);
            String coordinates = this.formatCoordinates();
            this.whisperCoordinates(playerName, coordinates);
            return 1;
        };
        ArgumentBuilder nameArgument = nameBuilder.executes(whisperAction);
        LiteralArgumentBuilder clipboardRoot = (LiteralArgumentBuilder) root.then(nameArgument);
        com.mojang.brigadier.Command<Object> clipboardAction = context -> {
            try {
                KeyboardHandler capturedKeyboard = ClientAccess.evernightMinecraft.keyboardHandler;
                String coordinates = this.formatCoordinates();
                capturedKeyboard.setClipboard(coordinates);
                MutableComponent confirmation = Component.literal(Translations.ui("commands.Coords.Copied"));
                MessageSignature signature = ChatUtils.createSignature(-1);
                ChatUtils.printChat(confirmation, signature);
            } catch (Exception ignoredFailure) {
            }
            return 1;
        };
        clipboardRoot.executes(clipboardAction);
    }

    private void whisperCoordinates(String recipient, String coordinates) {
        String whisperCommand = "w " + recipient + " " + coordinates;
        ClientPacketListener connection = ClientAccess.evernightMinecraft.player.connection;
        connection.sendCommand(whisperCommand);
    }
}