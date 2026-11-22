package evernight.mixin.minecraft.render;

import evernight.client.ClientAccess;
import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.events.render.GlintTextureTransformEvent;
import evernight.client.modules.render.Chams.ChamsModule;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TextureTransform.class)
public class TextureTransformMixin {
    @ModifyArgs(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;scale(F)Lorg/joml/Matrix4f;", remap = false))
    private static void modifyGlintScale(Args arguments) {
        GlintTextureTransformEvent event = GlintTextureTransformEvent.resetAndGet();
        ClientAccess.eventBus.post(event);
        arguments.set(0, arguments.<Float>get(0) * event.getScale());
    }

    @ModifyArgs(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;translation(FFF)Lorg/joml/Matrix4f;", remap = false))
    private static void applyGlintProgressTranslation(Args arguments) {
        if (MixinModuleRefs.chams.speedSetting.isAtMinimum() && ChamsModule.renderingGlintBatch) {
            float progress = (MixinModuleRefs.chams.progressSetting.getValue()).intValue() / 100.0F;
            arguments.set(0, -progress);
            arguments.set(1, progress);
        }
    }
}
