package evernight.mixin.minecraft.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import evernight.client.bootstrap.MixinModuleRefs;
import net.minecraft.world.level.block.HoneyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoneyBlock.class)
public class HoneyBlockMixin {
    @ModifyExpressionValue(
            method = "entityInside",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/HoneyBlock;isSlidingDown(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Z")
    )
    private boolean suppressHoneySliding(boolean originalSliding) {
        return (!MixinModuleRefs.noSlow.isToggled() || !MixinModuleRefs.noSlow.honeySetting.getValue()) && originalSliding;
    }
}
