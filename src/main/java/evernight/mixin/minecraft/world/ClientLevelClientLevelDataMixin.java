package evernight.mixin.minecraft.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.render.Skybox.SkyboxModule;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevelData.class)
public class ClientLevelClientLevelDataMixin {
    private static final SkyboxModule skyboxModule = ClientContext.getModule(SkyboxModule.class);

    @Inject(method = "getHorizonHeight", at = @At("HEAD"), cancellable = true)
    private void overrideHorizonHeightForSkybox(CallbackInfoReturnable<Double> callback) {
        if (ClientAccess.evernightMinecraft.level != null) {
            if (skyboxModule.isToggled() && skyboxModule.isCurrentDimensionEnabled()
                    && !ClientAccess.evernightMinecraft.level.dimension().identifier().getPath()
                    .contains("over")) {
                callback.setReturnValue(1.0);
                callback.cancel();
            }
        }
    }
}