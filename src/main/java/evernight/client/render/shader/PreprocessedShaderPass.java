package evernight.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import evernight.client.ClientContext;
import evernight.client.managers.registry.RenderTargetRegistry;
import evernight.client.modules.render.Shader.ShaderModule;
import evernight.client.render.color.ColorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.*;

public abstract class PreprocessedShaderPass extends PostProcessShaderPass {
    // Declared before the pre-passes below: static fields are initialized in declaration order,
    // so those constructors must not observe a null output target.
    private static final RenderTarget PRE_TARGET = RenderTargetRegistry.createRenderTarget("evernight_pre_pass");
    protected static final PrePass COVERAGE_PASS = new PrePass("multi/coverage.frag", "u_Coverage", true);
    protected static final PrePass DISTANCE_PASS = new PrePass("multi/distance.frag", "u_Distance", false);
    protected static final ShaderModule shaderModule = ClientContext.getModule(ShaderModule.class);
    private final PrePass prePass;

    protected PreprocessedShaderPass(String vertexResource, String fragmentResource, PrePass prePass) {
        super(vertexResource, fragmentResource);
        this.prePass = prePass;
    }

    @Override
    protected void prepareOutput() {
        if (!shaderModule.shouldSkipHandOutline() && shaderModule.lineWidthSetting.getValue() > 0 && this.prePass != null) {
            this.prePass.apply(POST_TARGET);
        }
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        if (this.prePass != null) {
            this.uniforms.set(this.prePass.samplerName, 16, PRE_TARGET);
        }
    }

    /**
     * Uploads the uniforms every outline style shares: overlay sampler, image switch, line width,
     * fast lines, glow weight, the decorator dots and the fill/outline colours.
     *
     * @param includeGlow whether the fragment shader declares the glow group
     *                    ({@code u_GlowInner}, {@code u_GlowQuality}, {@code u_Fade}); only
     *                    {@code solid.frag} does not
     */
    protected final void uploadSharedUniforms(boolean includeGlow) {
        ShaderUniforms program = this.uniforms;
        GL13.glActiveTexture(GL13.GL_TEXTURE7);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTextureId);
        program.set("u_Image", shaderModule.imageSetting.getValue());
        program.set("u_Overlay", 7);
        program.set("u_OverlayAlpha", shaderModule.getOverlayAlpha());
        int lineWidth = shaderModule.shouldSkipHandOutline() ? 0 : shaderModule.lineWidthSetting.getValue();
        program.set("u_Width", lineWidth);
        boolean fastLines = shaderModule.fastLinesSetting.getValue() || ClientContext.shaderRegistry.renderingHands;
        program.set("u_FastLines", fastLines);
        if (includeGlow) {
            program.set("u_GlowInner", shaderModule.innerSetting.getValue());
            program.set("u_GlowQuality", (int) shaderModule.qualitySetting.getValue());
            program.set("u_Fade", !shaderModule.fadeSetting.isAtMaximum());
        }
        program.set("u_Glow", shaderModule.weightSetting.getValue());
        program.set("u_Dots", shaderModule.typeSetting.getValue().ordinal());
        program.set("u_DotsRadius", (int) shaderModule.radiusSetting.getValue());
        program.set("u_DotsAlpha", shaderModule.alphaSetting.getValue());
        program.set(this.fillColorUniform(), this.uniformFillColor());
        program.set(this.outlineColorUniform(), shaderModule.outlineColorSetting.getValue());
    }

    /**
     * Uniform name of the fill colour; the {@code rainbow} shader uses a shorter name.
     */
    protected String fillColorUniform() {
        return "u_FillColor";
    }

    /**
     * Uniform name of the outline colour; the {@code rainbow} shader uses a shorter name.
     */
    protected String outlineColorUniform() {
        return "u_OutlineColor";
    }

    /**
     * Fill colour uploaded by {@link #uploadSharedUniforms(boolean)}. A style that hides the fill
     * while a hand is drawn, or drops it entirely like {@code solid}, overrides this.
     */
    protected Color uniformFillColor() {
        if (shaderModule.shouldSkipHandFill()) {
            return ColorUtils.transparent(shaderModule.outlineColorSetting.getValue());
        }
        return shaderModule.applyClusterAlpha(shaderModule.fillSetting.getValue());
    }

    protected static final class PrePass extends RenderTargetShaderStage {
        private final String samplerName;
        private final boolean coverage;

        private PrePass(String fragmentResource, String samplerName, boolean coverage) {
            super("base.vert", fragmentResource, PRE_TARGET);
            this.samplerName = samplerName;
            this.coverage = coverage;
        }

        @Override
        public void updateUniforms() {
            super.updateUniforms();
            this.uniforms.set("u_Width", shaderModule.lineWidthSetting.getValue());
            if (this.coverage) {
                this.uniforms.set("u_Step", shaderModule.qualitySetting.getValue());
            }
        }
    }
}
