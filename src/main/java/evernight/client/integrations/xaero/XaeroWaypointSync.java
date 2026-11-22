package evernight.client.integrations.xaero;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.utils.core.WaypointServerKey;
import evernight.client.utils.world.Waypoint;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Two-way bridge between the Xaero "Evernight Waypoints" set and the client waypoint registry.
 *
 * <p>Waypoints are matched by name inside the current server, dimension and Xaero world. New points
 * are mirrored to the other side, while points removed on either side are removed from both. The
 * bridge only tracks names it has mirrored itself, so unrelated client waypoints are never removed.
 *
 * <p>Disabling the bridge clears the Evernight waypoint set on the minimap; the client waypoints
 * themselves are left untouched.
 */
public final class XaeroWaypointSync implements WaypointSync, ClientAccess {
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final Map<String, Set<String>> mirroredNames = new HashMap<>();
    private boolean enabled;
    private int tickCounter;

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.mirroredNames.clear();
        this.tickCounter = 0;
        if (enabled) {
            ClientAccess.eventBus.subscribe(this);
            return;
        }
        ClientAccess.eventBus.unsubscribe(this);
        ClientContext.waypointAdapter.clearWaypoints();
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @EventHandler
    private void onMinecraftTick(MinecraftTickEvent event) {
        if (++this.tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        this.tickCounter = 0;
        this.synchronize();
    }

    private void synchronize() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null || ClientAccess.evernightMinecraft.level == null) {
            return;
        }
        String worldKey = XaeroWaypointAdapter.getCurrentWorldKey();
        if (worldKey == null) {
            return;
        }
        WaypointSet waypointSet = XaeroWaypointAdapter.getOrCreateWaypointSet();
        if (waypointSet == null) {
            return;
        }
        String server = WaypointServerKey.getCurrent();
        String dimension = WorldUtils.getDimension().getWaypointKey();
        String mirrorKey = server + "|" + dimension + "|" + worldKey;
        Set<String> mirrored = this.mirroredNames.computeIfAbsent(mirrorKey, ignoredKey -> new HashSet<>());
        Map<String, xaero.common.minimap.waypoints.Waypoint> minimapWaypoints = new LinkedHashMap<>();
        for (xaero.common.minimap.waypoints.Waypoint minimapWaypoint : waypointSet.getWaypoints()) {
            String key = normalizeName(minimapWaypoint.getName());
            if (!key.isEmpty()) {
                minimapWaypoints.put(key, minimapWaypoint);
            }
        }
        Map<String, Waypoint> clientWaypoints = new LinkedHashMap<>();
        for (Waypoint clientWaypoint : ClientContext.waypointRegistry.getRegistry()) {
            if (dimension.equals(clientWaypoint.getDimension()) && server.equalsIgnoreCase(clientWaypoint.getServer())) {
                String key = normalizeName(clientWaypoint.getName());
                if (!key.isEmpty()) {
                    clientWaypoints.put(key, clientWaypoint);
                }
            }
        }
        this.reconcileDeletions(mirrored, minimapWaypoints, clientWaypoints);
        for (Map.Entry<String, xaero.common.minimap.waypoints.Waypoint> entry : minimapWaypoints.entrySet()) {
            if (clientWaypoints.containsKey(entry.getKey())) {
                mirrored.add(entry.getKey());
                continue;
            }
            Waypoint createdWaypoint = this.createClientWaypoint(entry.getValue(), server, dimension);
            if (createdWaypoint != null) {
                clientWaypoints.put(entry.getKey(), createdWaypoint);
                mirrored.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Waypoint> entry : clientWaypoints.entrySet()) {
            if (minimapWaypoints.containsKey(entry.getKey())) {
                mirrored.add(entry.getKey());
                continue;
            }
            Waypoint clientWaypoint = entry.getValue();
            BlockPos waypointPosition = BlockPos.containing(
                    clientWaypoint.getX(), clientWaypoint.getY(), clientWaypoint.getZ()
            );
            if (ClientContext.waypointAdapter.addWaypoint(clientWaypoint.getName(), waypointPosition)) {
                mirrored.add(entry.getKey());
            }
        }
    }

    private void reconcileDeletions(
            Set<String> mirrored,
            Map<String, xaero.common.minimap.waypoints.Waypoint> minimapWaypoints,
            Map<String, Waypoint> clientWaypoints
    ) {
        Iterator<String> iterator = mirrored.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            xaero.common.minimap.waypoints.Waypoint minimapWaypoint = minimapWaypoints.get(key);
            Waypoint clientWaypoint = clientWaypoints.get(key);
            if (minimapWaypoint != null && clientWaypoint != null) {
                continue;
            }
            if (minimapWaypoint != null) {
                ClientContext.waypointAdapter.removeWaypoints(minimapWaypoint.getName());
                minimapWaypoints.remove(key);
            } else if (clientWaypoint != null) {
                ClientContext.waypointRegistry.unregisterWaypoint(clientWaypoint);
                clientWaypoints.remove(key);
            }
            iterator.remove();
        }
    }

    private Waypoint createClientWaypoint(
            xaero.common.minimap.waypoints.Waypoint minimapWaypoint, String server, String dimension
    ) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        double waypointY = minimapWaypoint.isYIncluded() ? minimapWaypoint.getY() : player.getY();
        Waypoint clientWaypoint = new Waypoint(
                minimapWaypoint.getName(),
                minimapWaypoint.getX() + 0.5D,
                waypointY,
                minimapWaypoint.getZ() + 0.5D,
                dimension,
                server
        );
        return ClientContext.waypointRegistry.registerWaypoint(clientWaypoint) ? clientWaypoint : null;
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
