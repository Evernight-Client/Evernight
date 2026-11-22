package evernight.client.render.util;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public interface EntityRenderStateAccess {
    static Entity getEntity(EntityRenderState renderState) {
        EntityRenderStateAccess access = (EntityRenderStateAccess) renderState;
        return access.evernight$getEntity();
    }

    static void setEntity(EntityRenderState renderState, Entity entity) {
        EntityRenderStateAccess access = (EntityRenderStateAccess) renderState;
        access.evernight$setEntity(entity);
    }

    Entity evernight$getEntity();

    void evernight$setEntity(Entity entity);
}