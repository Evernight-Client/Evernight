package evernight.client.render.shader;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.render.RenderStateManager;
import evernight.client.managers.registry.RenderTargetRegistry;
import evernight.client.managers.registry.ShaderRegistry;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import evernight.client.render.mesh.ColorOutlineBufferSource;
import evernight.client.render.util.RenderUtils;

public class PostProcessShaderPass extends BaseShaderPass {
    public static final RenderTarget POST_TARGET;
    private static final Identifier OVERLAY_TEXTURE;
    protected static int overlayTextureId;

    static {
        String overlayPath = "textures/overlay.png";
        String overlayNamespace = "evernight-mount";
        OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath(overlayNamespace, overlayPath);
        POST_TARGET = RenderTargetRegistry.createRenderTarget("post");
        overlayTextureId = -1;
    }

    public PostProcessShaderPass(String vertexResource, String fragmentResource) {
        super(vertexResource, fragmentResource);
    }

    @Override
    public void updateUniforms() {
        ShaderUniforms projectionUniforms = this.uniforms;
        Matrix4f projectionMatrix = RenderUtils.WORLD_PROJECTION_MATRIX;
        projectionUniforms.set("u_Proj", projectionMatrix);
        ShaderUniforms modelViewUniforms = this.uniforms;
        Matrix4fStack modelViewMatrix = RenderSystem.getModelViewStack();
        modelViewUniforms.set("u_ModelView", modelViewMatrix);
        ShaderUniforms sizeUniforms = this.uniforms;
        float capturedWidth = (ClientAccess.evernightMinecraft.getWindow()).getWidth();
        float height = (ClientAccess.evernightMinecraft.getWindow()).getHeight();
        sizeUniforms.set("u_Size", capturedWidth, height);
        this.uniforms.set("u_Texture", 15);
        this.uniforms.set("u_Time", (float) GLFW.glfwGetTime());
    }

    @Override
    public void render(Runnable draw) {
        TextureManager capturedTextureManager = ClientAccess.evernightMinecraft.getTextureManager();
        Identifier overlayResource = OVERLAY_TEXTURE;
        AbstractTexture overlayTexture = capturedTextureManager.getTexture(overlayResource);
        GlTexture overlayGlTexture = (GlTexture) overlayTexture.getTexture();
        overlayTextureId = overlayGlTexture.glId();
        ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
        RenderUtils.clearColor(POST_TARGET);
        RenderUtils.clearDepth(POST_TARGET);
        this.renderToTarget(draw);
        if (buffers.hasColor()) {
            buffers.reset();
            this.prepareOutput();
            GlTexture postGlTexture = (GlTexture) POST_TARGET.getColorTexture();
            int postTextureId = postGlTexture.glId();
            ShaderPassRenderState renderState = ShaderPassRenderState.INSTANCE;
            renderState.configure(this, postTextureId);
            RenderStateManager.addStates(ShaderPassRenderState.INSTANCE);
            RenderUtils.drawFullscreenQuad();
            RenderStateManager.reset();
        }
    }

    protected void prepareOutput() {
    }

    protected void renderToTarget(Runnable draw) {
        ColorOutlineBufferSource buffers = ShaderRegistry.getBufferSource();
        RenderTargetUtils.overrideRenderTargets(POST_TARGET);
        ClientContext.shaderRegistry.renderingIntoTarget = true;
        draw.run();
        buffers.endBatchAndClean();
        ClientContext.shaderRegistry.renderingIntoTarget = false;
        RenderTargetUtils.restoreRenderTargets();
    }

}