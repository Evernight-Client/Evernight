package evernight.client.modules.render.Tracers;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;

public abstract class TracerRenderer implements ClientAccess {
    protected final TracersModule module;

    public TracerRenderer(TracersModule module) {
        this.module = module;
    }

    public abstract void render(PoseStack poseStack, float tickDelta);
}