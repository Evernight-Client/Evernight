package evernight.client.events.player;

import net.minecraft.core.BlockPos;
import evernight.client.events.core.CancellableEvent;

public final class CrystalPlacementEvent extends CancellableEvent {
    private final BlockPos pos;

    public CrystalPlacementEvent(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}