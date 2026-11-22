package evernight.mixin.minecraft.entity;

import evernight.client.bootstrap.MixinModuleRefs;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobMixin {
    @Inject(method = "isSaddled", at = @At("HEAD"), cancellable = true)
    private void forceSaddledForEntityControl(CallbackInfoReturnable<Boolean> callback) {
        if (MixinModuleRefs.entityControl.isToggled()) {
            callback.setReturnValue(true);
        }
    }
}
