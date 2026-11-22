package evernight.mixin.minecraft.network;

import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundPongResponsePacket.class)
public interface ClientboundPongResponsePacketAccessor {
    @Mutable
    @Accessor("time")
    long evernight$startTime();

    @Mutable
    @Accessor("time")
    void evernight$startTime(long time);
}