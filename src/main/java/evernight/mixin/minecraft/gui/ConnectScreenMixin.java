package evernight.mixin.minecraft.gui;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.ConnectScreenConnectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import evernight.mixin.minecraft.network.ConnectionAccessor;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Shadow
    @Nullable
    private volatile Connection connection;
    @Shadow
    @Final
    private Screen parent;
    @Shadow
    @Final
    private Component connectFailedTitle;

    @Inject(method = "connect", at = @At("HEAD"))
    private void recordServerConnection(
            Minecraft minecraft, ServerAddress address, ServerData serverData, TransferState transferState, CallbackInfo callback
    ) {
        ServerData resolvedServerData = serverData;
        if (resolvedServerData == null) {
            resolvedServerData = new ServerData("Evernight", address.getHost() + ":" + address.getPort(), Type.OTHER);
        }
        // Handlers read the current ServerData (LogoutSpots/TotemPopTracker compare the event address
        // against it), so it has to be installed before the event is dispatched.
        ClientContext.serverStateManager.setServerData(resolvedServerData);
        ClientAccess.eventBus.post(new ConnectScreenConnectEvent(resolvedServerData.ip));
    }

    /**
     * Leaves the connecting screen when the socket died before any packet listener handled it.
     *
     * <p>Vanilla only shows a {@link DisconnectedScreen} from
     * {@code Connection#handleDisconnection} when a listener is installed. A server that drops the
     * connection during the login handshake can leave both listeners null; the disconnection is then
     * marked as handled silently and {@link ConnectScreen#tick()} keeps calling it every tick, which
     * leaves the player on "Connecting..." forever (and spams "handleDisconnection() called twice").
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void leaveConnectingScreenAfterSilentDrop(CallbackInfo callback) {
        Connection pendingConnection = this.connection;
        if (pendingConnection == null || pendingConnection.isConnected()) {
            return;
        }
        if (!((ConnectionAccessor) pendingConnection).isDisconnectionHandled()
                || pendingConnection.getPacketListener() != null) {
            return;
        }
        DisconnectionDetails details = pendingConnection.getDisconnectionDetails();
        String reason = details == null ? "<none>" : details.reason().getString();
        ClientAccess.logger.warn("ConnectScreen: connection closed before a listener handled it, reason: {}", reason);
        if (details == null) {
            details = new DisconnectionDetails(Component.translatable("disconnect.endOfStream"));
        }
        ClientAccess.evernightMinecraft.setScreen(
                new DisconnectedScreen(this.parent, this.connectFailedTitle, details));
        callback.cancel();
    }
}