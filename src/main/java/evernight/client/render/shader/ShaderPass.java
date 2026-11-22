package evernight.client.render.shader;

public interface ShaderPass {
    long START_TIME_MILLIS = System.currentTimeMillis();

    CompiledShaderProgram getProgram();

    void updateUniforms();

    void render(Runnable draw);
}