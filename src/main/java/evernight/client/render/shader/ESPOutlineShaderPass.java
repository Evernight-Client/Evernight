package evernight.client.render.shader;

import evernight.client.ClientContext;
import evernight.client.modules.render.ESP.ESPModule;

public class ESPOutlineShaderPass extends PostProcessShaderPass {
    private static final ESPModule espModule;

    static {
        espModule = ClientContext.getModule(ESPModule.class);
    }

    public ESPOutlineShaderPass() {
        super("base.vert", "outline.frag");
        this.initializeProgram();
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        ShaderUniforms widthUniforms = this.uniforms;
        widthUniforms.set("u_Width", 2);
        ShaderUniforms glowUniforms = this.uniforms;
        glowUniforms.set("u_GlowMultiplier", 1.0F);
        ShaderUniforms fillOpacityUniforms = this.uniforms;
        float fillOpacity = (espModule.blockFillOpacitySetting.getValue());
        fillOpacityUniforms.set("u_FillOpacity", fillOpacity);
    }
}