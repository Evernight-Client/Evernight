package evernight.client.render.shader;

public class BloomShaderPass extends PreprocessedShaderPass {
    public BloomShaderPass() {
        super("base.vert", "bloom.frag", COVERAGE_PASS);
        this.initializeProgram();
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        this.uploadSharedUniforms(true);
    }
}
