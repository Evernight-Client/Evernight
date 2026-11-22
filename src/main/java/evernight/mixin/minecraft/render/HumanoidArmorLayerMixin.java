package evernight.mixin.minecraft.render;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.modules.render.NoRender.NoRenderModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {
    private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void suppressArmorSubmission(
            PoseStack poses, SubmitNodeCollector collector, int packedLight, HumanoidRenderState renderState, float ignoredRenderFloat1, float ignoredRenderFloat2, CallbackInfo callback
    ) {
        if (noRenderModule.getArmorOpacity() == 0.0F) {
            callback.cancel();
        }
    }
}