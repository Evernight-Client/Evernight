package evernight.client.events.world;

import net.minecraft.world.entity.Entity;
import evernight.client.events.core.CancellableEvent;

public final class FishingHookPullEntityEvent extends CancellableEvent {
    private final Entity entity;

    public FishingHookPullEntityEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return this.entity;
    }
}