package evernight.client.render.shader;

import evernight.client.render.color.ColorUtils;

import java.awt.*;

public class SolidShaderPass extends PreprocessedShaderPass {
    public SolidShaderPass() {
        super("base.vert", "solid.frag", DISTANCE_PASS);
        this.initializeProgram();
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        this.uploadSharedUniforms(false);
        this.uniforms.set("u_ShapeMode", 2);
    }

    @Override
    protected Color uniformFillColor() {
        if (shaderModule.shouldSkipHandFill()) {
            return ColorUtils.TRANSPARENT;
        }
        return shaderModule.applyClusterAlpha(shaderModule.fillSetting.getValue());
    }
}
