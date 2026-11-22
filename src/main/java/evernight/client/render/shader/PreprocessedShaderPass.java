package evernight.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import evernight.client.ClientContext;
import evernight.client.managers.registry.RenderTargetRegistry;
import evernight.client.modules.render.Shader.ShaderModule;

public abstract class PreprocessedShaderPass extends PostProcessShaderPass {
    // Declared before the pre-passes below: static fields are initialized in declaration order,
    // so those constructors must not observe a null output target.
    private static final RenderTarget PRE_TARGET = RenderTargetRegistry.createRenderTarget("evernight_pre_pass");
    protected static final PrePass COVERAGE_PASS = new PrePass("multi/coverage.frag", "u_Coverage", true);
    protected static final PrePass DISTANCE_PASS = new PrePass("multi/distance.frag", "u_Distance", false);
    private static final ShaderModule shaderModule = ClientContext.getModule(ShaderModule.class);
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