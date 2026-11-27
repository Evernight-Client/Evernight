package evernight.client.render.shader;

public class RainbowShaderPass extends PreprocessedShaderPass {
    public RainbowShaderPass() {
        super("base.vert", "rainbow.frag", COVERAGE_PASS);
        this.initializeProgram();
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        this.uploadSharedUniforms(true);
        ShaderUniforms program = this.uniforms;
        float elapsedMillis = (float) (System.currentTimeMillis() - START_TIME_MILLIS);
        program.set("u_Offset", elapsedMillis * 0.001F * shaderModule.rainbowSpeedSetting.getValue());
        program.set("u_Strength", shaderModule.fillStrengthSetting.getValue());
        program.set("u_StrengthLine", shaderModule.lineStrengthSetting.getValue());
    }

    @Override
    protected String fillColorUniform() {
        return "u_Fill";
    }

    @Override
    protected String outlineColorUniform() {
        return "u_Outline";
    }
}
