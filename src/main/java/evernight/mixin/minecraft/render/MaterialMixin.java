package evernight.mixin.minecraft.render;

import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.render.util.RenderUtils;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Material.class)
public class MaterialMixin {
    @Shadow
    @Final
    private Identifier atlasLocation;

    @Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
    private void applyTranslucentHandMaterial(Function renderTypeFactory, CallbackInfoReturnable<RenderType> callback) {
        if (MixinModuleRefs.noRender.getHandOpacity() != 1.0F && RenderUtils.getRenderColor()[3] != 255) {
            callback.setReturnValue(RenderTypes.entityTranslucent(this.atlasLocation));
            callback.cancel();
        }
    }
}
