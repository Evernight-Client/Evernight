package evernight.mixin.minecraft.render;

import evernight.client.ClientContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderer.class)
public interface BlockEntityRendererMixin {
    @Inject(method = "getViewDistance", at = @At("HEAD"), cancellable = true)
    private void evernight$overrideViewDistance(CallbackInfoReturnable<Integer> callback) {
        if (ClientContext.shaderRegistry.renderingIntoTarget) {
            callback.setReturnValue(1024);
        }
    }
}