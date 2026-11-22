package evernight.mixin.minecraft.render;

import com.mojang.blaze3d.font.GlyphInfo;
import evernight.client.ClientContext;
import evernight.client.modules.render.NoRender.NoRenderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphInfo.class)
public interface GlyphInfoMixin {
    @Inject(method = "getShadowOffset", at = @At("HEAD"), cancellable = true)
    default void modifyTextShadowOffset(CallbackInfoReturnable<Float> callback) {
        NoRenderModule noRenderModule = ClientContext.moduleRegistry
                .getModule(NoRenderModule.class);
        if (noRenderModule.isToggled() && noRenderModule.textShadowSetting.getValue()) {
            callback.setReturnValue(0.6F);
        } else {
            callback.setReturnValue(1.0F);
        }
    }
}