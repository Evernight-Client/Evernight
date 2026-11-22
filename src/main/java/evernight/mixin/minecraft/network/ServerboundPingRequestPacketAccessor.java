package evernight.mixin.minecraft.network;

import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundPingRequestPacket.class)
public interface ServerboundPingRequestPacketAccessor {
    @Accessor("time")
    @Mutable
    void evernight$startTime(long time);

    @Accessor("time")
    @Mutable
    long evernight$startTime();
}