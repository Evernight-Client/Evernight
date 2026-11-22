package evernight.client.integrations.xaero;

import net.minecraft.core.BlockPos;

public interface WaypointAdapter {
    void removeWaypoints(String name);

    boolean isAvailable();

    boolean addWaypoint(String name, BlockPos position);

    void clearWaypoints();
}
