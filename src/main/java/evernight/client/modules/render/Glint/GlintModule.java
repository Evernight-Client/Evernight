package evernight.client.modules.render.Glint;

import com.mojang.blaze3d.textures.GpuTextureView;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.render.GlintTextureTransformEvent;
import evernight.client.events.render.RenderTypeDrawEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.awt.*;
import evernight.client.modules.render.Chams.ChamsModule;

public class GlintModule extends Module {
    private static final Vector4f GLINT_COLOR_VECTOR;
    private static final Identifier SHINE_TEXTURE;

    static {
        GLINT_COLOR_VECTOR = new Vector4f();
        SHINE_TEXTURE = Identifier.fromNamespaceAndPath("evernight-mount", "textures/shine.png");
    }

    public final Setting<Color> colorSetting;
    private final Setting<Integer> spreadSetting;

    public GlintModule() {
        super("Glint", ModuleCategory.RENDER);
        this.colorSetting = this.add(new ColorSetting("Color", new Color(-1, true)));
        this.spreadSetting = this.add(new NumberSetting<>("Spread", 1, 1, 5));
    }

    public static float getSpreadMultiplier(int spread) {
        switch (spread) {
            case 2:
                return 0.24F;
            case 3:
                return 0.32F;
            case 4:
                return 0.42F;
            case 5:
                return 0.5F;
            default:
                return 0.16F;
        }
    }

    @EventHandler
    private void onGlintTextureTransform(GlintTextureTransformEvent event) {
        if (!ChamsModule.renderingGlintBatch) {
            int spreadChoice = this.spreadSetting.getValue();
            float scale = 6.25F * getSpreadMultiplier(spreadChoice);
            event.setScale(scale);
        }
    }

    @EventHandler(priority = 100)
    private void onRenderTypeDrawEvent(RenderTypeDrawEvent event) {
        if (ChamsModule.renderingGlintBatch || EventStage.Pre != event.getStage()) {
            return;
        }
        TextureManager textureManager = (Minecraft.getInstance()).getTextureManager();
        Identifier textureId = SHINE_TEXTURE;
        AbstractTexture texture = textureManager.getTexture(textureId);
        GpuTextureView textureView = texture.getTextureView();
        event.setOverride(textureView);
    }

    public Vector4f getGlintColor() {
        Color selectedColor = this.colorSetting.getValue();
        Vector4f colorVector = GLINT_COLOR_VECTOR;
        float red = selectedColor.getRed();
        red /= 255.0F;
        float green = selectedColor.getGreen();
        green /= 255.0F;
        float blue = selectedColor.getBlue();
        blue /= 255.0F;
        colorVector.set(red, green, blue, 1.0F);
        return GLINT_COLOR_VECTOR;
    }
}