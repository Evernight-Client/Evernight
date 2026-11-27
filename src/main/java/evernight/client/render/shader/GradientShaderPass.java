package evernight.client.render.shader;

import evernight.client.render.color.ColorUtils;

import java.awt.*;

public class GradientShaderPass extends PreprocessedShaderPass {
    public GradientShaderPass() {
        super("base.vert", "gradient.frag", COVERAGE_PASS);
        this.initializeProgram();
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        this.uploadSharedUniforms(true);
        ShaderUniforms program = this.uniforms;
        program.set("u_Smooth", shaderModule.smoothSetting.getValue());
        float elapsedMillis = (float) (System.currentTimeMillis() - START_TIME_MILLIS);
        float animationTime = elapsedMillis * 0.01F * shaderModule.speedSetting.getValue() * 0.15F;
        program.set("u_Time", animationTime);
        if (shaderModule.shouldSkipHandFill()) {
            program.set("u_FillColor2", ColorUtils.transparent(shaderModule.fillSecondSetting.getValue()));
            program.set("u_FillColor3", ColorUtils.transparent(shaderModule.fillSecondSetting.getValue()));
        } else {
            Color blendedFill = shaderModule.blendSettingColors(shaderModule.fillSetting, shaderModule.fillSecondSetting);
            program.set("u_FillColor2", shaderModule.applyClusterAlpha(blendedFill));
            program.set("u_FillColor3", shaderModule.applyClusterAlpha(shaderModule.fillSecondSetting.getValue()));
        }
        Color blendedOutline = shaderModule.blendSettingColors(shaderModule.outlineColorSetting, shaderModule.outlineSecondSetting);
        program.set("u_OutlineColor2", shaderModule.applyClusterAlpha(blendedOutline));
        program.set("u_OutlineColor3", shaderModule.outlineSecondSetting.getValue());
        program.set("u_Step", shaderModule.stepSetting.getValue() * 4.0F);
    }

    @Override
    protected Color uniformFillColor() {
        if (shaderModule.shouldSkipHandFill()) {
            return ColorUtils.transparent(shaderModule.fillSetting.getValue());
        }
        return shaderModule.applyClusterAlpha(shaderModule.fillSetting.getValue());
    }
}
