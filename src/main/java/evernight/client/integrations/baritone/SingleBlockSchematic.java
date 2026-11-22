package evernight.client.integrations.baritone;

import baritone.api.schematic.AbstractSchematic;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * One block schematic used to make Baritone place a single block.
 *
 * <p>The builder treats it like any other build: it walks to the origin, pulls the matching item out of
 * the inventory and places it, which keeps block placement inside the pathing/placement code that
 * already handles reach, obstruction and item swapping.
 */
final class SingleBlockSchematic extends AbstractSchematic {
    private final BlockState wantedState;

    SingleBlockSchematic(BlockState wantedState) {
        super(1, 1, 1);
        this.wantedState = wantedState;
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState currentState, List<BlockState> approxPlaceable) {
        return this.wantedState;
    }
}
