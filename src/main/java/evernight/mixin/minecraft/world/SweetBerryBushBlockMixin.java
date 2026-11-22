package evernight.mixin.minecraft.world;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import evernight.client.bootstrap.MixinModuleRefs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SweetBerryBushBlock.class)
public class SweetBerryBushBlockMixin {
    @WrapWithCondition(
            method = "entityInside",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V")
    )
    private boolean allowBerryBushSlowdown(Entity entity, BlockState blockState, Vec3 slowdown) {
        return !MixinModuleRefs.noSlow.isToggled() || !MixinModuleRefs.noSlow.berryBushSetting.getValue();
    }
}
