package evernight.mixin.minecraft.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import evernight.client.ClientContext;
import evernight.client.modules.misc.NoPacketKick.NoPacketKickModule;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Legacy decoder hook of {@code NoPacketKick}: consumes the rest of a frame whose packet payload
 * failed to decode, then rethrows the original {@link DecoderException}.
 *
 * <p>The connection is kept alive by {@code ConnectionMixin} swallowing the exception. This is the
 * historical Evernight behaviour and only runs while the module is enabled and {@code Enhanced} is
 * off; enhanced mode replaces it with {@link PacketDecoderMixin}.
 */
@Mixin(IdDispatchCodec.class)
public class IdDispatchCodecMixin {
    @WrapOperation(method = "decode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object decodeWithLegacyPacketKickHandling(StreamCodec<?, ?> codec, Object input,
                                                      Operation<Object> original) {
        NoPacketKickModule module = ClientContext.getModule(NoPacketKickModule.class);
        if (module == null || !module.shouldSkipLegacyDecodeError()) {
            return original.call(codec, input);
        }
        try {
            return original.call(codec, input);
        } catch (DecoderException decoderFailure) {
            if (input instanceof ByteBuf buffer) {
                buffer.skipBytes(buffer.readableBytes());
            }
            module.logInterceptedException(decoderFailure, NoPacketKickModule.InterceptionType.DECODE);
            throw decoderFailure;
        }
    }
}
