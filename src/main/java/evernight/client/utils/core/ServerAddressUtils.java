package evernight.client.utils.core;

import evernight.client.ClientAccess;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

import java.net.SocketAddress;

public final class ServerAddressUtils {
    private static final String fallbackServerAddress;

    static {
        fallbackServerAddress = "singleplayer";
    }

    private ServerAddressUtils() {
    }

    public static String getCurrentServerAddress() {
        String address = null;
        try {
            ClientPacketListener packetListener = ClientAccess.evernightMinecraft.getConnection();
            Connection connection = packetListener.getConnection();
            SocketAddress remoteAddress = connection.getRemoteAddress();
            address = remoteAddress.toString();
            int separatorIndex = address.indexOf(':');
            if (separatorIndex > 0) {
                address = address.substring(0, separatorIndex);
            }
            while (address.endsWith(".")) {
                address = address.substring(0, address.length() - 1);
            }
        } catch (Throwable ignoredFailure) {
        }
        return address != null && !address.isEmpty() ? address : fallbackServerAddress;
    }
}