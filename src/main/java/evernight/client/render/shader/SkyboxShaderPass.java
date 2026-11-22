package evernight.client.render.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import evernight.client.ClientContext;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.render.RenderStateManager;
import evernight.client.modules.render.Skybox.SkyboxModule;
import evernight.client.utils.core.UncheckedExceptions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkyboxShaderPass extends BaseShaderPass implements ReloadableShaderPass {
    private static final SkyboxModule SKYBOX_MODULE;
    private static final int CONFIG_BINDING = 0;
    private static final int CONFIG_SIZE = 48;

    static {
        SKYBOX_MODULE = ClientContext.getModule(SkyboxModule.class);
    }

    private int configBlock = GL31C.GL_INVALID_INDEX;
    private int configBuffer;

    public SkyboxShaderPass() {
        super("sky/base.vert", "sky/sky.frag");
        this.reload(this);
    }

    @Override
    public void reload(BaseShaderPass pass) {
        ReloadableShaderPass.super.reload(pass);
        int programId = this.program.programId();
        this.configBlock = GL31C.glGetUniformBlockIndex(programId, "Config");
        if (this.configBlock != GL31C.GL_INVALID_INDEX) {
            GL31C.glUniformBlockBinding(programId, this.configBlock, CONFIG_BINDING);
        }
    }

    @Override
    public void updateUniforms() {
        Minecraft client = Minecraft.getInstance();
        RenderTarget target = client.getMainRenderTarget();
        int screenWidth = target.width;
        int screenHeight = target.height;
        float aspectRatio = (float) screenWidth / screenHeight;
        Camera camera = client.gameRenderer.getMainCamera();
        float parallax = SKYBOX_MODULE.getParallax();
        float mouseX = camera.yRot() / 180.0F * aspectRatio * parallax;
        float mouseY = -camera.xRot() / 90.0F * parallax;
        float elapsedTime = (float) (System.currentTimeMillis() - START_TIME_MILLIS) / 1000.0F;
        Color color = SKYBOX_MODULE.getShaderColor();
        if (this.configBlock == GL31C.GL_INVALID_INDEX) {
            this.uniforms.set("u_Scale", 1.0F);
            this.uniforms.set("u_Mouse", mouseX, mouseY);
            this.uniforms.set("u_Resolution", screenWidth, screenHeight);
            this.uniforms.set("u_Time", elapsedTime);
            this.uniforms.set("u_Color", color);
            return;
        }
        if (this.configBuffer == 0) {
            this.configBuffer = GL15C.glGenBuffers();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer config = Std140Builder.onStack(stack, CONFIG_SIZE)
                    .putVec4(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F)
                    .putVec2(screenWidth, screenHeight).putVec2(mouseX, mouseY).putFloat(1.0F).putFloat(elapsedTime)
                    .align(16).get();
            GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, this.configBuffer);
            GL15C.glBufferData(GL31C.GL_UNIFORM_BUFFER, config, GL15C.GL_STREAM_DRAW);
            GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, CONFIG_BINDING, this.configBuffer);
        }
    }

    @Override
    public void render(Runnable draw) {
        boolean usesConfig = this.configBlock != GL31C.GL_INVALID_INDEX;
        int previousBuffer = usesConfig ? GL11C.glGetInteger(GL31C.GL_UNIFORM_BUFFER_BINDING) : 0;
        int previousIndexedBuffer = usesConfig ? GL30C.glGetIntegeri(GL31C.GL_UNIFORM_BUFFER_BINDING, CONFIG_BINDING) : 0;
        long previousStart = previousIndexedBuffer != 0 ? GL32C.glGetInteger64i(GL31C.GL_UNIFORM_BUFFER_START, CONFIG_BINDING) : 0;
        long previousSize = previousIndexedBuffer != 0 ? GL32C.glGetInteger64i(GL31C.GL_UNIFORM_BUFFER_SIZE, CONFIG_BINDING) : 0;
        ShaderPassRenderState state = ShaderPassRenderState.INSTANCE;
        state.configure(this);
        RenderStateManager.addStates(state);
        try {
            draw.run();
        } finally {
            RenderStateManager.reset();
            if (usesConfig) {
                if (previousSize > 0) {
                    GL30C.glBindBufferRange(GL31C.GL_UNIFORM_BUFFER, CONFIG_BINDING, previousIndexedBuffer, previousStart, previousSize);
                } else {
                    GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, CONFIG_BINDING, previousIndexedBuffer);
                }
                GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, previousBuffer);
            }
        }
    }

    @Override
    public CompiledShaderProgram compileProgram() {
        Path shaderDirectory = ConfigurationManager.SHADERS_DIRECTORY;
        Path fragmentPath = shaderDirectory.resolve("sky.frag");
        String fragmentSource;
        try {
            fragmentSource = Files.readString(fragmentPath);
        } catch (Throwable failure) {
            throw UncheckedExceptions.rethrow(failure);
        }
        String vertexSource = RenderTargetUtils.readShaderSource(this.vertexResource);
        String vertexSourceForCompile = vertexSource;
        return CompiledShaderProgram.compile(vertexSourceForCompile, fragmentSource);
    }
}