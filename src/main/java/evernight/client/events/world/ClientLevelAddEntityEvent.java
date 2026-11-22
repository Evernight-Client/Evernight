package evernight.client.events.world;

import net.minecraft.world.entity.Entity;
import evernight.client.events.core.CancellableEvent;

public final class ClientLevelAddEntityEvent extends CancellableEvent {
    private final Entity entity;

    public ClientLevelAddEntityEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return this.entity;
    }
}