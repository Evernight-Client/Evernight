package evernight.client.utils.core;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.client.WaypointSettings.WaypointSettingsModule;
import evernight.client.settings.options.ServerKeyMode;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Resolves the key used to group client waypoints per server.
 *
 * <p>Address mode keeps the historical behaviour and uses the remote address. Name mode uses the
 * name saved in the multiplayer server list; connections without a usable name share one key so
 * they are treated as the same server.
 */
public final class WaypointServerKey {
    private static final String SINGLEPLAYER_KEY = "singleplayer";
    private static final String UNKNOWN_KEY = "unknown";

    private WaypointServerKey() {
    }

    public static String getCurrent() {
        WaypointSettingsModule settings = ClientContext.getModule(WaypointSettingsModule.class);
        if (settings != null && settings.getServerKeyMode() == ServerKeyMode.NAME) {
            return getCurrentName();
        }
        return ServerAddressUtils.getCurrentServerAddress();
    }

    private static String getCurrentName() {
        ServerData serverData = ClientAccess.evernightMinecraft.getCurrentServer();
        if (serverData == null) {
            return SINGLEPLAYER_KEY;
        }
        String name = serverData.name;
        return name == null || name.isBlank() ? UNKNOWN_KEY : name;
    }
}
