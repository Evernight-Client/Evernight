package evernight.mixin.minecraft.entity;

import evernight.client.ClientContext;
import evernight.client.modules.misc.SkinFlicker.SkinFlickerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets {@link SkinFlickerModule} keep the local player's render stable while the option model parts keep flickering
 * for everyone else. Only the local player is affected; remote players always use the synced model parts.
 */
@Mixin(Avatar.class)
public abstract class AvatarMixin {
    @Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
    private void suppressLocalSkinFlicker(PlayerModelPart part, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        SkinFlickerModule skinFlickerModule = ClientContext.getModule(SkinFlickerModule.class);
        if (skinFlickerModule == null || !skinFlickerModule.shouldSuppressLocalFlicker()) {
            return;
        }
        callback.setReturnValue(skinFlickerModule.getSavedModelPart(part));
    }
}
