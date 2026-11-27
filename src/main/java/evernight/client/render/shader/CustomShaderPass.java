package evernight.client.render.shader;

import evernight.client.managers.config.ConfigurationManager;
import evernight.client.utils.core.UncheckedExceptions;

import java.nio.file.Files;
import java.nio.file.Path;

public class CustomShaderPass extends PreprocessedShaderPass implements ReloadableShaderPass {
    public CustomShaderPass() {
        super("base.vert", "custom.frag", COVERAGE_PASS);
        this.reload(this);
    }

    @Override
    public void updateUniforms() {
        super.updateUniforms();
        this.uploadSharedUniforms(true);
        float elapsedMillis = (float) (System.currentTimeMillis() - START_TIME_MILLIS);
        this.uniforms.set("u_Time", elapsedMillis / 1000.0F);
    }

    @Override
    public CompiledShaderProgram compileProgram() {
        Path shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY;
        Path customFragmentPath = shaderDirectory.resolve("custom.frag");
        String vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource);
        String fragmentSource = RenderTargetUtils.readShaderSource(this.fragmentResource);
        String customMainImage;
        try {
            customMainImage = Files.readString(customFragmentPath);
        } catch (Throwable failure) {
            throw UncheckedExceptions.rethrow(failure);
        }
        String templateFragmentSource = fragmentSource;
        fragmentSource = templateFragmentSource.replaceFirst("vec4\\s+mainImage\\s*\\(\\s*bool\\s+outline\\s*\\)\\s*\\{[\\s\\S]*?\\}", customMainImage);
        return CompiledShaderProgram.compile(vertexSource, fragmentSource);
    }
}
