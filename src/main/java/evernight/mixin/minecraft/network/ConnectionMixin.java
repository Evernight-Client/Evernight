package evernight.mixin.minecraft.network;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.PacketEvent;
import evernight.client.modules.exploit.PingSpoof.PingSpoofModule;
import evernight.client.modules.misc.NoPacketKick.NoPacketKickModule;
import evernight.client.utils.network.PingSpoofDelayHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.SkipPacketException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    private static final PingSpoofModule pingSpoofModule = ClientContext.getModule(PingSpoofModule.class);
    @Shadow
    private Channel channel;
    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void installPingSpoofDelayHandler(ChannelHandlerContext context, CallbackInfo callback) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }
        ChannelPipeline pipeline = context.pipeline();
        if (pipeline.get(PingSpoofDelayHandler.NAME) == null) {
            pipeline.addFirst(PingSpoofDelayHandler.NAME, new PingSpoofDelayHandler(
                    pingSpoofModule, () -> ((Connection) (Object) this).getPacketListener() instanceof ClientGamePacketListener
            ));
        }
    }

    @Inject(method = "channelRead0*", at = @At("HEAD"), cancellable = true)
    public void onPacketReceive(ChannelHandlerContext context, Packet packet, CallbackInfo callback) {
        if (this.channel.isOpen() && packet != null) {
            try {
                PacketEvent.Receive event = new PacketEvent.Receive(packet);
                ClientAccess.eventBus.post(event);
                if (event.isCancelled()) {
                    callback.cancel();
                }
            } catch (Exception ignoredException) {
            }
        }
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onPacketSend(
            Packet packet, ChannelFutureListener listener, boolean flush, CallbackInfo callback) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            try {
                PacketEvent.Send event = new PacketEvent.Send(packet);
                ClientAccess.eventBus.post(event);
                if (event.isCancelled()) {
                    callback.cancel();
                }
            } catch (Exception ignoredException) {
            }
        }
    }

    @Inject(method = "doSendPacket", at = @At("TAIL"))
    private void onPacketPostSend(
            Packet packet, ChannelFutureListener listener, boolean flush, CallbackInfo callback) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            try {
                PacketEvent.PostSend event = new PacketEvent.PostSend(packet);
                ClientAccess.eventBus.post(event);
            } catch (Exception ignoredException) {
            }
        }
    }

    /**
     * Keeps the connection alive when the netty pipeline reports an exception.
     *
     * <p>Legacy mode (module enabled, {@code Enhanced} off) preserves the historical behaviour and
     * swallows every throwable. Enhanced mode is narrower: only
     * {@link io.netty.handler.codec.DecoderException} in the {@code PLAY} protocol of the clientbound
     * stream is intercepted, skippable packets are left to {@link Connection#exceptionCaught}, and the
     * module caps how many faults it tolerates so a permanently desynchronised stream still disconnects
     * eventually.
     */
    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void onConnectionException(ChannelHandlerContext context, Throwable failure, CallbackInfo callback
    ) {
        NoPacketKickModule module = ClientContext.getModule(NoPacketKickModule.class);
        if (module == null || failure instanceof SkipPacketException) {
            return;
        }
        if (module.shouldUseLegacyInterception()) {
            module.logInterceptedException(failure, NoPacketKickModule.InterceptionType.CONNECTION);
            callback.cancel();
            return;
        }
        PacketListener packetListener = ((Connection) (Object) this).getPacketListener();
        if (this.receiving != PacketFlow.CLIENTBOUND || packetListener == null
                || !module.shouldIgnoreConnectionError(failure, packetListener.protocol())) {
            return;
        }
        module.logInterceptedException(failure, NoPacketKickModule.InterceptionType.CONNECTION);
        callback.cancel();
    }
}