package evernight.mixin.minecraft.render;

import evernight.client.render.util.EntityRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityRenderStateAccess {
    @Unique
    private Entity renderedEntity;

    @Override
    public Entity evernight$getEntity() {
        return this.renderedEntity;
    }

    @Override
    public void evernight$setEntity(Entity entity) {
        this.renderedEntity = entity;
    }
}