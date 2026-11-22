package evernight.mixin.minecraft.network;

import evernight.client.ClientContext;
import evernight.client.modules.misc.NoPacketKick.NoPacketKickModule;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin implements PacketListener {
    /**
     * Legacy behaviour: hide the disconnect screen when the kick reason embeds a decoder exception.
     *
     * <p>Only runs while the module is enabled and {@code Enhanced} is off. Note that at this point the
     * connection is already closed, so the player stays in the world without a server; enhanced mode
     * avoids that by stopping the disconnect at its source instead ({@link #ignorePacketHandlerError}).
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
    private void suppressLegacyDecoderExceptionDisconnect(DisconnectionDetails details, CallbackInfo callback) {
        NoPacketKickModule module = ClientContext.getModule(NoPacketKickModule.class);
        if (module == null || this.protocol() != ConnectionProtocol.PLAY) {
            return;
        }
        if (!module.shouldSuppressLegacyDisconnect(details.reason().getString())) {
            return;
        }
        callback.cancel();
    }

    /**
     * Enhanced mode: keeps the player connected when a packet handler throws.
     *
     * <p>Vanilla shows the {@code Network Protocol Error} screen from
     * {@link ClientCommonPacketListenerImpl#onPacketError}, which is reached from
     * {@code PacketProcessor} for every exception thrown on the client thread while handling a packet.
     * Cancelling the head of that method drops the offending packet and leaves the session alive; the
     * exception is still logged (and mirrored into chat when the module asks for it).
     */
    @Inject(method = "onPacketError", at = @At("HEAD"), cancellable = true)
    private void ignorePacketHandlerError(Packet<?> packet, Exception exception, CallbackInfo callback) {
        NoPacketKickModule module = ClientContext.getModule(NoPacketKickModule.class);
        if (module == null || !module.shouldIgnoreHandlerError(this.protocol())) {
            return;
        }
        module.logInterceptedException(exception, NoPacketKickModule.InterceptionType.HANDLER);
        callback.cancel();
    }
}
