package evernight.client.events.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import evernight.client.events.core.CancellableEvent;

public final class NameTagRenderEvent extends CancellableEvent {
    private final PoseStack poseStack;
    private final Entity entity;

    public NameTagRenderEvent(PoseStack poseStack, Entity entity) {
        this.poseStack = poseStack;
        this.entity = entity;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public PoseStack getPoseStack() {
        return this.poseStack;
    }
}