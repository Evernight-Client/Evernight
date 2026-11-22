package evernight.mixin.minecraft.network;

import evernight.client.utils.network.ImportantPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerboundUseItemOnPacket.class)
public class ServerboundUseItemOnPacketMixin implements ImportantPacket {
    @Unique
    private boolean important;

    @Override
    public void evernight$markImportant() {
        this.important = true;
    }

    @Override
    public boolean evernight$isImportant() {
        return this.important;
    }
}