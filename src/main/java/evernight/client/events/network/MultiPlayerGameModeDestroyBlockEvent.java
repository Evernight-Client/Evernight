package evernight.client.events.network;

import net.minecraft.core.BlockPos;
import evernight.client.events.core.CancellableEvent;

public class MultiPlayerGameModeDestroyBlockEvent extends CancellableEvent {
    public BlockPos pos;

    public MultiPlayerGameModeDestroyBlockEvent(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}