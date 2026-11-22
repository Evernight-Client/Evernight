package evernight.mixin.minecraft.network;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(ClientboundExplodePacket.class)
public interface ClientboundExplodePacketAccessor {
    @Mutable
    @Accessor("playerKnockback")
    void setVelocity(Optional playerKnockback);
}