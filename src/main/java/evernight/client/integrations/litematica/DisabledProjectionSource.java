package evernight.client.integrations.litematica;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fallback used when Litematica is not installed, or when its API could not be linked at runtime.
 */
public final class DisabledProjectionSource implements ProjectionSource {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public @Nullable BlockState getProjectedState(BlockPos pos) {
        return null;
    }

    @Override
    public @Nullable String getPlacementName() {
        return null;
    }
}
