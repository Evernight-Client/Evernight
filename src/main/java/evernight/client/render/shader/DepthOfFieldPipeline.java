package evernight.client.render.shader;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.render.Ambience.AmbienceModule;
import evernight.client.settings.core.Setting;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class DepthOfFieldPipeline extends ShaderPipeline {
    private static final AmbienceModule ambience;

    static {
        ambience = ClientContext.getModule(AmbienceModule.class);
    }

    public DepthOfFieldPipeline() {
        super(ClientAccess.evernightMinecraft.getMainRenderTarget());
        CompiledShaderProgram program = CompiledShaderProgram.fromResources(
                "base.vert", "multi/dof.frag"
        );
        ShaderStage verticalStage = new DepthOfFieldPipeline.DirectionalStage(
                program, false, MaskShaderRenderer.VERTICAL_TARGET);
        this.addStage(verticalStage);
        ShaderStage horizontalStage = new DepthOfFieldPipeline.DirectionalStage(
                program, true, MaskShaderRenderer.HORIZONTAL_TARGET);
        this.addStage(horizontalStage);
    }

    public static class DirectionalStage extends RenderTargetShaderStage {
        private final int directionX;
        private final int directionY;

        public DirectionalStage(CompiledShaderProgram program, boolean horizontal, RenderTarget outputTarget) {
            super(program, outputTarget);
            this.directionX = horizontal ? 1 : 0;
            this.directionY = horizontal ? 0 : 1;
        }

        @Override
        public void updateUniforms() {
            super.updateUniforms();
            GL13.glActiveTexture(33998);
            AmbienceModule capturedAmbience = DepthOfFieldPipeline.ambience;
            RenderTarget depthTarget = capturedAmbience.getDepthRenderTarget();
            GpuTexture depthTexture = depthTarget.getDepthTexture();
            GlTexture glTexture = (GlTexture) depthTexture;
            int depthTextureId = glTexture.glId();
            GL11.glBindTexture(3553, depthTextureId);
            ShaderUniforms capturedDepthUniforms = this.uniforms;
            capturedDepthUniforms.set("DepthSampler", 14);
            ShaderUniforms capturedFocusUniforms = this.uniforms;
            float focusDistance = 0.998F;
            capturedFocusUniforms.set("FocusDistance", focusDistance);
            ShaderUniforms capturedStrengthUniforms = this.uniforms;
            Setting<Float> strengthOption = DepthOfFieldPipeline.ambience.strengthSetting;
            Float strengthValue = strengthOption.getValue();
            float rawStrength = strengthValue;
            float blurStrength = rawStrength * 10.0F;
            capturedStrengthUniforms.set("BlurStrength", blurStrength);
            ShaderUniforms capturedDirectionUniforms = this.uniforms;
            float capturedXDirection = this.directionX;
            float yDirection = this.directionY;
            capturedDirectionUniforms.set("u_Direction", capturedXDirection, yDirection);
        }
    }
}