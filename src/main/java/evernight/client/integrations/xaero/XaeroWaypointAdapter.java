package evernight.client.integrations.xaero;

import net.minecraft.core.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.module.HudModule;

import java.util.ArrayList;
import java.util.List;

public final class XaeroWaypointAdapter implements WaypointAdapter {
    private static final String WAYPOINT_SET_NAME = "Evernight Waypoints";

    private static int waypointX(BlockPos position) {
        return position.getX();
    }

    private static String getWaypointSymbol(String name) {
        int nameLength = name.length();
        byte maximumLength = 1;
        int symbolLength = Math.min(maximumLength, nameLength);
        byte startIndex = 0;
        return name.substring(startIndex, symbolLength);
    }

    private static WaypointSet getCurrentWaypointSet() {
        MinimapWorld world = getCurrentWorld();
        if (world == null) {
            return null;
        }
        String setName = WAYPOINT_SET_NAME;
        return getOrCreateWaypointSet(world, setName);
    }

    private static MinimapWorld getCurrentWorld() {
        HudModule minimapModule = BuiltInHudModules.MINIMAP;
        MinimapSession session = (MinimapSession) minimapModule.getCurrentSession();
        if (session == null) {
            return null;
        }
        MinimapWorldManager worldManager = session.getWorldManager();
        return worldManager.getCurrentWorld();
    }

    private static WaypointSet getOrCreateWaypointSet(MinimapWorld world, String name) {
        WaypointSet waypointSet = world.getWaypointSet(name);
        if (waypointSet == null) {
            world.addWaypointSet(name);
            waypointSet = world.getWaypointSet(name);
        }
        return waypointSet;
    }

    /**
     * The Evernight waypoint set of the current world without creating it when absent.
     */
    static WaypointSet getExistingWaypointSet() {
        MinimapWorld world = getCurrentWorld();
        return world == null ? null : world.getWaypointSet(WAYPOINT_SET_NAME);
    }

    /**
     * The Evernight waypoint set of the current world, created on demand.
     */
    static WaypointSet getOrCreateWaypointSet() {
        return getCurrentWaypointSet();
    }

    /**
     * Stable key of the current Xaero world, used to scope the waypoint sync state.
     */
    static String getCurrentWorldKey() {
        MinimapWorld world = getCurrentWorld();
        return world == null ? null : world.getFullPath().toString();
    }

    private static boolean hasWaypointAt(WaypointSet waypointSet, BlockPos position) {
        for (Waypoint existingWaypoint : waypointSet.getWaypoints()) {
            if (existingWaypoint.getX() == position.getX() && existingWaypoint.getZ() == position.getZ()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean addWaypoint(String name, BlockPos position) {
        WaypointSet waypointSet = getCurrentWaypointSet();
        if (waypointSet == null || hasWaypointAt(waypointSet, position)) {
            return false;
        }
        Waypoint waypoint = new Waypoint(
                waypointX(position), position.getY(), position.getZ(), name, getWaypointSymbol(name), WaypointColor.getRandom()
        );
        waypointSet.add(waypoint);
        return true;
    }

    @Override
    public void removeWaypoints(String name) {
        WaypointSet waypointSet = getCurrentWaypointSet();
        if (waypointSet == null) {
            return;
        }
        List<Waypoint> matchingWaypoints = new ArrayList<>();
        for (Waypoint existingWaypoint : waypointSet.getWaypoints()) {
            if (existingWaypoint.getName().equals(name)) {
                matchingWaypoints.add(existingWaypoint);
            }
        }
        waypointSet.removeAll(matchingWaypoints);
    }

    @Override
    public void clearWaypoints() {
        WaypointSet waypointSet = getExistingWaypointSet();
        if (waypointSet != null) {
            waypointSet.clear();
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
