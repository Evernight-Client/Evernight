package evernight.client.integrations.xaero;

import net.minecraft.core.BlockPos;

public final class DisabledWaypointAdapter implements WaypointAdapter {
    @Override
    public void removeWaypoints(String name) {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean addWaypoint(String name, BlockPos position) {
        return false;
    }

    @Override
    public void clearWaypoints() {
    }
}
