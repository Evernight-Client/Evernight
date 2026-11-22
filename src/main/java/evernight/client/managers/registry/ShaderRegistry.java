package evernight.client.managers.registry;

import evernight.client.ClientAccess;
import evernight.client.gui.components.setting.ColorSubmitNodeStorage;
import evernight.client.render.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.resources.model.AtlasManager;
import evernight.client.render.mesh.ColorOutlineBufferSource;
import evernight.client.render.mesh.DiscardingBufferSource;
import evernight.client.render.mesh.NoOpOutlineBufferSource;
import evernight.client.render.shader.BaseShaderPass;
import evernight.client.render.shader.BloomMaskPipeline;
import evernight.client.render.shader.BloomShaderPass;
import evernight.client.render.shader.BlurMaskPipeline;
import evernight.client.render.shader.CustomShaderPass;
import evernight.client.render.shader.DepthOfFieldPipeline;
import evernight.client.render.shader.ESPOutlineShaderPass;
import evernight.client.render.shader.GlowyShaderPipeline;
import evernight.client.render.shader.GradientShaderPass;
import evernight.client.render.shader.RainbowShaderPass;
import evernight.client.render.shader.ReloadableShaderPass;
import evernight.client.render.shader.ShaderPass;
import evernight.client.render.shader.SkyboxShaderPass;
import evernight.client.render.shader.SolidShaderPass;

public final class ShaderRegistry implements ClientAccess {
    private static final ColorSubmitNodeStorage submitNodes;
    private static FeatureRenderDispatcher featureDispatcher;
    private static ColorOutlineBufferSource bufferSource;

    static {
        submitNodes = new ColorSubmitNodeStorage();
    }

    private final Object2ObjectOpenHashMap<String, ShaderPass> passes = new Object2ObjectOpenHashMap<>();
    public boolean renderingIntoTarget;
    public boolean renderingHands;

    public ShaderRegistry() {
        this.register("outline", new ESPOutlineShaderPass());
        this.register("solid", new SolidShaderPass());
        this.register("rainbow", new RainbowShaderPass());
        this.register("gradient", new GradientShaderPass());
        this.register("bloom", new BloomShaderPass());
        this.register("custom", new CustomShaderPass());
        this.register("sky", new SkyboxShaderPass());
        this.register("dof", new DepthOfFieldPipeline());
        this.register("blur_mask", new BlurMaskPipeline());
        this.register("bloom_mask", new BloomMaskPipeline());
        this.register("glowy", new GlowyShaderPipeline());
    }

    private static FeatureRenderDispatcher getFeatureDispatcher() {
        if (featureDispatcher == null) {
            featureDispatcher = new FeatureRenderDispatcher(submitNodes, readBlockRenderer(),
                    new ColorOutlineBufferSource.SuppliedBufferSource(ShaderRegistry::getBufferSource),
                    readAtlasManager(), NoOpOutlineBufferSource.INSTANCE, DiscardingBufferSource.INSTANCE,
                    ClientAccess.evernightMinecraft.font);
        }
        return featureDispatcher;
    }

    private static BlockRenderDispatcher readBlockRenderer() {
        return ClientAccess.evernightMinecraft.getBlockRenderer();
    }

    private static AtlasManager readAtlasManager() {
        return ClientAccess.evernightMinecraft.getAtlasManager();
    }

    public static ColorSubmitNodeStorage getSubmitNodes() {
        return submitNodes;
    }

    public static ColorOutlineBufferSource getBufferSource() {
        if (bufferSource == null) {
            bufferSource = new ColorOutlineBufferSource();
        }
        return bufferSource;
    }

    public static void finishFeatureRendering() {
        FeatureRenderDispatcher dispatcher = getFeatureDispatcher();
        dispatcher.renderAllFeatures();
        ColorSubmitNodeStorage nodes = getSubmitNodes();
        nodes.endFrame();
    }

    public static void setRenderColor(int color) {
        getBufferSource().setColorArgb(color);
        getSubmitNodes().setColorArgb(color);
    }

    public ShaderPass getPass(String id) {
        return this.passes.get(id);
    }

    public void reloadAll() {
        for (ShaderPass pass : this.passes.values()) {
            if (pass instanceof ReloadableShaderPass reloadable) {
                reloadable.reload((BaseShaderPass) pass);
            }
        }
    }

    private void register(String id, ShaderPass pass) {
        this.passes.put(id, pass);
    }

    public void render(String id, Runnable draw) {
        ShaderPass pass = this.getPass(id);
        pass.render(draw);
    }
}