package evernight.mixin.minecraft.render;

import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.modules.render.Chams.ChamsModule;
import evernight.client.render.shader.ClientRenderPipelines;
import evernight.client.render.util.RenderUtils;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderType.class)
public class RenderTypeColorMixin {
    @Shadow
    @Final
    public RenderSetup state;

    @ModifyArg(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            ), index = 1)
    private Vector4fc applyRenderColorModifiers(Vector4fc originalColor) {
        Vector4f mutableColor = (Vector4f) originalColor;
        if (ClientRenderPipelines.isGlint(this.state.pipeline) && MixinModuleRefs.glint.isToggled()) {
            mutableColor.mul(MixinModuleRefs.glint.getGlintColor());
        }
        if (ClientRenderPipelines.isGlint(this.state.pipeline) && ChamsModule.renderingGlintBatch) {
            mutableColor.set(MixinModuleRefs.chams.getGlintColor(), 1.0F);
        }
        if (RenderUtils.getRenderColor()[3] != 255) {
            mutableColor.mul(1.0F, 1.0F, 1.0F, Mth.clamp(RenderUtils.getRenderColor()[3]
                    / 255.0F, 0.0F, 1.0F));
        }
        return mutableColor;
    }
}
