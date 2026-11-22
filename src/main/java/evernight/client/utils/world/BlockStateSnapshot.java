package evernight.client.utils.world;

import evernight.client.ClientAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public record BlockStateSnapshot(BlockState state, BlockPos position, long capturedAtMillis
) implements ClientAccess {
    public static BlockState captureState(BlockPos position) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        return level.getBlockState(position);
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() - this.capturedAtMillis >= 500L;
    }
}