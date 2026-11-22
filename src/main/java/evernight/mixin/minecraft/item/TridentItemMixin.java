package evernight.mixin.minecraft.item;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import evernight.client.bootstrap.MixinModuleRefs;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TridentItem.class)
public class TridentItemMixin {
    @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
    private boolean allowRiptideOnRelease(boolean inWaterOrRain) {
        return inWaterOrRain || MixinModuleRefs.trident.isAlwaysRiptideEnabled();
    }

    @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"))
    private boolean allowRiptideOnUse(boolean inWaterOrRain) {
        return inWaterOrRain || MixinModuleRefs.trident.isAlwaysRiptideEnabled();
    }

    @ModifyArgs(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
    private void scaleRiptidePush(Args arguments) {
        arguments.set(0, arguments.<Double>get(0) * MixinModuleRefs.trident.getBoostMultiplier());
        arguments.set(1, arguments.<Double>get(1) * MixinModuleRefs.trident.getBoostMultiplier());
        arguments.set(2, arguments.<Double>get(2) * MixinModuleRefs.trident.getBoostMultiplier());
    }

    @ModifyExpressionValue(method = "releaseUsing", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int removeMinimumChargeTime(int originalChargeTime) {
        return MixinModuleRefs.trident.isToggled() ? 0 : originalChargeTime;
    }
}
