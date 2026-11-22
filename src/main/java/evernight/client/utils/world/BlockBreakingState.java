package evernight.client.utils.world;

import net.minecraft.core.BlockPos;

public interface BlockBreakingState {
    BlockPos getCurrentBreakingBlock();

    void setCurrentBreakingBlock(BlockPos position);

    float getBreakingProgress();

    void setBreakingProgress(float progress);

    boolean isBreakingBlock();

    void setBreakingBlock(boolean breaking);
}