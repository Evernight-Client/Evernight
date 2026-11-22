package evernight.client.events.network;

import net.minecraft.world.phys.Vec3;
import evernight.client.events.core.CancellableEvent;

public final class ClientPacketListenerHandleMoveVehicleEvent extends CancellableEvent {
    private final Vec3 position;

    public ClientPacketListenerHandleMoveVehicleEvent(Vec3 position) {
        this.position = position;
    }

    public Vec3 getPosition() {
        return this.position;
    }
}