package evernight.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface LevelRendererFramebufferAccess {
    void evernight$popFramebuffer();

    void evernight$pushFramebuffer(RenderTarget target);
}