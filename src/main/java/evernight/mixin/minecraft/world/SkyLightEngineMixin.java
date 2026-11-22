package evernight.mixin.minecraft.world;

import evernight.client.ClientContext;
import evernight.client.modules.render.NoRender.NoRenderModule;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightEngine.class)
public class SkyLightEngineMixin {
    private static final NoRenderModule noRenderModule = ClientContext.getModule(NoRenderModule.class);

    @Inject(at = @At("HEAD"), method = "getLowestSourceY", cancellable = true)
    private void suppressSkyLightSource(
            int chunkX, int chunkZ, int blockY, CallbackInfoReturnable<Integer> callback) {
        if (noRenderModule.isToggled() && noRenderModule.skyLightSetting.getValue()) {
            callback.setReturnValue(15);
            callback.cancel();
        }
    }
}