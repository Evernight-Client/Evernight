package evernight.client.events.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import evernight.client.events.core.CancellableEvent;

public final class MultiPlayerGameModeStartDestroyBlockEvent extends CancellableEvent {
    private final BlockPos pos;
    private final Direction direction;

    public MultiPlayerGameModeStartDestroyBlockEvent(BlockPos pos, Direction direction) {
        this.pos = pos;
        this.direction = direction;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Direction getDirection() {
        return this.direction;
    }
}