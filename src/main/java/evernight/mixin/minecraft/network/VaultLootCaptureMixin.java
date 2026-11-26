package evernight.mixin.minecraft.network;

import evernight.client.utils.vault.VaultLootCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the vault loot capture with the three packets that describe one ominous vault opening.
 *
 * <p>All injections sit at {@code RETURN}: vanilla has already applied the packet, so the block state
 * and the item entity read here are the final values. The order inside one tick is preserved because
 * every step runs on the client thread in packet order.
 */
@Mixin(ClientPacketListener.class)
public class VaultLootCaptureMixin {
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void captureBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo callback) {
        VaultLootCapture.onBlockUpdate(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleAddEntity", at = @At("RETURN"))
    private void captureEntitySpawn(ClientboundAddEntityPacket packet, CallbackInfo callback) {
        VaultLootCapture.onEntitySpawn(packet.getId(), packet.getType(), packet.getX(), packet.getY(), packet.getZ());
    }

    @Inject(method = "handleSetEntityData", at = @At("RETURN"))
    private void captureEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        if (client.level.getEntity(packet.id()) instanceof ItemEntity item) {
            VaultLootCapture.onEntityData(packet.id(), item);
        }
    }
}
