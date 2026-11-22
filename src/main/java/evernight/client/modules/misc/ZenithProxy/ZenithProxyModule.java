package evernight.client.modules.misc.ZenithProxy;

import evernight.client.ClientAccess;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

/**
 * Client-side integrations for a ZenithProxy instance.
 *
 * <p>A ZenithProxy server advertises the {@code ZenithProxy} brand and accepts two chat commands
 * from a connected client: {@code /swap} hands control of the bot to the player and
 * {@code /disconnect} closes the proxy account's own connection. Joining as a spectator is a
 * cookie handshake performed before the login phase, so the multiplayer list gets its own entry
 * point for it (see {@code ServerSelectionListMixin}).
 */
public final class ZenithProxyModule extends Module {
    private static final String ZENITH_BRAND = "ZenithProxy";

    public final Setting<Boolean> serverListButtonSetting;
    public final Setting<Boolean> pauseMenuButtonsSetting;
    public final Setting<Boolean> closeProxySetting;

    public ZenithProxyModule() {
        super("ZenithProxy",
                ModuleCategory.MISC,
                "zenith", "zproxy");
        this.serverListButtonSetting = this.add(new BooleanSetting("ServerListButton", true));
        this.pauseMenuButtonsSetting = this.add(new BooleanSetting("PauseMenuButtons", true));
        this.closeProxySetting = this.add(new BooleanSetting("CloseProxy", true));
    }

    /**
     * Whether the current connection goes through a ZenithProxy instance.
     *
     * <p>The brand is only available once the play phase started; while still on the multiplayer
     * screen the server list entry carries the same string as its version name, so that is used as
     * the fallback.
     */
    public static boolean isZenithServer() {
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        if (minecraft == null) {
            return false;
        }
        ClientPacketListener listener = minecraft.getConnection();
        if (listener != null) {
            String brand = listener.serverBrand();
            if (brand != null) {
                return brand.contains(ZENITH_BRAND);
            }
        }
        ServerData server = minecraft.getCurrentServer();
        if (server == null || server.version == null) {
            return false;
        }
        String version = server.version.tryCollapseToString();
        return version != null && version.contains(ZENITH_BRAND);
    }

    public boolean shouldShowServerListButton() {
        return this.isToggled() && this.serverListButtonSetting.getValue();
    }

    public boolean shouldShowPauseMenuButtons() {
        return this.isToggled() && this.pauseMenuButtonsSetting.getValue() && isZenithServer();
    }

    /**
     * Whether an automatic logout should shut down the proxy account instead of only dropping the
     * local client. Takes precedence over the illegal-disconnect path in AutoLog.
     */
    public boolean shouldCloseProxy() {
        return this.isToggled() && this.closeProxySetting.getValue() && isZenithServer();
    }

    /** Asks the proxy to disconnect its own account; the proxy then drops this client. */
    public void closeProxy() {
        this.sendProxyCommand("disconnect");
    }

    /** Asks the proxy to hand control of the bot back to this player. */
    public void swapSpectator() {
        this.sendProxyCommand("swap");
    }

    private void sendProxyCommand(String command) {
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        if (minecraft == null) {
            return;
        }
        ClientPacketListener listener = minecraft.getConnection();
        if (listener == null) {
            return;
        }
        listener.send(new ServerboundChatCommandPacket(command));
    }
}
