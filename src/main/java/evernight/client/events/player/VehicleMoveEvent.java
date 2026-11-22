package evernight.client.events.player;

import evernight.client.ClientAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class VehicleMoveEvent extends LocalPlayerMoveEvent {
    public VehicleMoveEvent(Vec3 velocity) {
        super(velocity, null);
    }

    public static boolean isLocalPlayerVehicle(Object candidate) {
        return ClientAccess.evernightMinecraft.player != null && candidate instanceof Entity && ClientAccess.evernightMinecraft.player.getVehicle()
                == candidate;
    }
}