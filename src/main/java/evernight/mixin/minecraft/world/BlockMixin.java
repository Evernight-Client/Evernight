package evernight.mixin.minecraft.world;

import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.modules.render.Xray.XrayModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void overrideXrayFaceVisibility(
            BlockState blockState, BlockState adjacentState, Direction face, CallbackInfoReturnable<Boolean> callback
    ) {
        if (XrayModule.getInstance().isToggled() && XrayModule.getInstance().shouldHideUnselectedBlocks()) {
            callback.cancel();
            callback.setReturnValue(XrayModule.getInstance().shouldRenderBlock(BlockPos.ZERO,
                    blockState.getBlock()));
        }
    }

    @Inject(method = "getFriction", at = @At("HEAD"), cancellable = true)
    private void overrideNoSlowFriction(CallbackInfoReturnable<Float> callback) {
        if (MixinModuleRefs.noSlow.isToggled()) {
            Block block = (Block) (Object) this;
            if (MixinModuleRefs.noSlow.iceSetting.getValue() && (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE)) {
                callback.setReturnValue(0.6F);
            }
            if (MixinModuleRefs.noSlow.slimeSetting.getValue() && block == Blocks.SLIME_BLOCK) {
                callback.setReturnValue(0.6F);
            }
            if (MixinModuleRefs.noSlow.honeySetting.getValue() && block == Blocks.HONEY_BLOCK) {
                callback.setReturnValue(0.6F);
            }
        }
    }
}
