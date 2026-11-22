package evernight.mixin.minecraft.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import evernight.client.ClientContext;
import evernight.client.modules.misc.NoPacketKick.NoPacketKickModule;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Downgrades a packet decode failure from "disconnect the player" to "drop this frame".
 *
 * <p>This is the enhanced-mode counterpart of {@link IdDispatchCodecMixin} and only runs while the
 * {@code Enhanced} switch of {@code NoPacketKick} is on.
 *
 * <p>{@link PacketDecoder#decode} hands one length-prefixed frame to the protocol codec. If that codec
 * throws (unknown packet id, malformed payload, unsupported custom payload, ...), the exception escapes
 * the decoder, the cumulation keeps the already partially read frame and netty ends up closing the
 * channel with an {@code Internal Exception}. The frame is therefore fully consumed here first, and the
 * failure is rethrown as {@link SkipPacketDecoderException}: vanilla then skips the rest of the buffer
 * and {@code Connection#exceptionCaught} only logs it, which keeps the session alive and the stream in
 * sync for the next frame.
 */
@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {
    @Shadow
    @Final
    private ProtocolInfo<?> protocolInfo;

    @WrapOperation(method = "decode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object skipUndecodablePacket(StreamCodec<?, ?> codec, Object input, Operation<Object> original) {
        NoPacketKickModule module = ClientContext.getModule(NoPacketKickModule.class);
        if (module == null || this.protocolInfo.flow() != PacketFlow.CLIENTBOUND
                || !module.shouldSkipDecodeError(this.protocolInfo.id())) {
            return original.call(codec, input);
        }
        try {
            return original.call(codec, input);
        } catch (Exception failure) {
            if (input instanceof ByteBuf buffer) {
                buffer.skipBytes(buffer.readableBytes());
            }
            module.logInterceptedException(failure, NoPacketKickModule.InterceptionType.DECODE);
            throw new SkipPacketDecoderException(failure);
        }
    }
}
