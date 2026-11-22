package evernight.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import evernight.client.ClientAccess;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

public final class RenderTargetUtils implements ClientAccess {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Deque<TargetState> savedTargets = new ArrayDeque<>();

    private RenderTargetUtils() {
        throw new AssertionError();
    }

    public static void popFramebuffer() {
        ((LevelRendererFramebufferAccess) ClientAccess.evernightMinecraft.levelRenderer).evernight$popFramebuffer();
    }

    public static void restoreRenderTargets() {
        TargetState previous = savedTargets.pop();
        popFramebuffer();
        setMainRenderTarget(previous.mainTarget());
        RenderSystem.outputColorTextureOverride = previous.colorOverride();
        RenderSystem.outputDepthTextureOverride = previous.depthOverride();
    }

    public static void pushFramebuffer(RenderTarget target) {
        ((LevelRendererFramebufferAccess) ClientAccess.evernightMinecraft.levelRenderer)
                .evernight$pushFramebuffer(target);
    }

    public static void overrideRenderTargets(RenderTarget target) {
        TargetState previous = new TargetState(ClientAccess.evernightMinecraft.getMainRenderTarget(),
                RenderSystem.outputColorTextureOverride, RenderSystem.outputDepthTextureOverride);
        pushFramebuffer(target);
        savedTargets.push(previous);
        setMainRenderTarget(target);
        // RenderType prefers these overrides over its render target.
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
    }

    public static void setMainRenderTarget(RenderTarget target) {
        ((MinecraftAccessor) ClientAccess.evernightMinecraft).evernight$setFramebuffer(target);
    }

    public static String readShaderSource(String resourcePath) {
        try {
            InputStream stream = BaseShaderPass.class.getResourceAsStream("/assets/evernight/shaders/" + resourcePath);
            assert stream != null;
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (IOException | AssertionError failure) {
            LOGGER.warn("Could not read shader source {}", resourcePath, failure);
            return "";
        }
    }

    private record TargetState(RenderTarget mainTarget, GpuTextureView colorOverride, GpuTextureView depthOverride) {
    }
}