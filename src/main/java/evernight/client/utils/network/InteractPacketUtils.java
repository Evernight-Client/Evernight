package evernight.client.utils.network;

import evernight.client.ClientAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.entity.Entity;

public final class InteractPacketUtils implements ClientAccess {
    private InteractPacketUtils() {
    }

    public static Entity getEntity(ServerboundInteractPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buffer);
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        int entityId = buffer.readVarInt();
        return level.getEntity(entityId);
    }

    public static Action getAction(ServerboundInteractPacket packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buffer);
        buffer.readVarInt();
        Class<Action> actionType = Action.class;
        return buffer.readEnum(actionType);
    }

    public enum Action {
        INTERACT, ATTACK, INTERACT_AT
    }
}