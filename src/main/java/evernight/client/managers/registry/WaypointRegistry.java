package evernight.client.managers.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.events.bus.EventBus;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.utils.core.WaypointServerKey;
import evernight.client.utils.world.Waypoint;
import net.minecraft.client.player.LocalPlayer;

import java.util.*;

public final class WaypointRegistry extends AbstractRegistry<Waypoint> implements ClientAccess, JsonConfigurable {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public WaypointRegistry() {
        super(new ArrayList<>());
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public boolean register(Waypoint waypoint) {
        return this.registerWaypoint(waypoint);
    }

    @Override
    public boolean unregister(Waypoint waypoint) {
        return this.unregisterWaypoint(waypoint);
    }

    public List<Waypoint> getVisible() {
        if (this.isWorldUnavailable()) {
            return Collections.emptyList();
        }
        String currentServer = WaypointServerKey.getCurrent();
        return this.entries.stream().filter(candidate -> {
            if (candidate.getServer() != null) {
                String waypointServer = candidate.getServer();
                return currentServer.equalsIgnoreCase(waypointServer);
            }
            return false;
        }).sorted(Comparator.comparing((Waypoint waypoint) -> {
            LocalPlayer playerSnapshot = ClientAccess.evernightMinecraft.player;
            double waypointX = waypoint.getX();
            double waypointY = waypoint.getY();
            double waypointZ = waypoint.getZ();
            return playerSnapshot.distanceToSqr(waypointX, waypointY, waypointZ);
        })).toList();
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject configObject = json.getAsJsonObject();
        if (!configObject.has("waypoints")) {
            return;
        }
        configObject = json.getAsJsonObject();
        JsonArray waypointArray = configObject.getAsJsonArray("waypoints");
        Iterator<JsonElement> iterator = waypointArray.iterator();
        while (iterator.hasNext()) {
            JsonElement element = iterator.next();
            try {
                Collection destination = this.entries;
                Gson gson = ClientAccess.gson;
                Class<Waypoint> waypointClass = Waypoint.class;
                destination.add(gson.fromJson(element, waypointClass));
            } catch (Exception loadFailure) {
                LOGGER.warn("Could not read waypoint entry", loadFailure);
            }
        }
    }

    @Override
    public JsonElement toJson() {
        JsonArray waypointArray = new JsonArray();
        JsonObject configObject = new JsonObject();
        Iterator<Waypoint> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            Waypoint waypoint = iterator.next();
            Gson gson = ClientAccess.gson;
            JsonElement waypointJson = gson.toJsonTree(waypoint);
            waypointArray.add(waypointJson);
        }
        configObject.add("waypoints", waypointArray);
        return configObject;
    }

    @Override
    public String getConfigName() {
        return "waypoints.json";
    }

    public boolean registerWaypoint(Waypoint waypoint) {
        Iterator<Waypoint> iterator = this.getRegistry().iterator();
        while (iterator.hasNext()) {
            Waypoint existingWaypoint = iterator.next();
            String existingName = existingWaypoint.getName();
            String requestedName = waypoint.getName();
            if (existingName.equalsIgnoreCase(requestedName)) {
                String existingServer = existingWaypoint.getServer();
                String requestedServer = waypoint.getServer();
                if (existingServer.equalsIgnoreCase(requestedServer)) {
                    return false;
                }
            }
        }
        return this.entries.add(waypoint);
    }

    public boolean unregisterWaypoint(Waypoint waypoint) {
        return this.entries.removeIf(candidate -> {
            if (candidate != null && waypoint != null) {
                String candidateName = candidate.getName();
                String requestedName = waypoint.getName();
                if (candidateName.equalsIgnoreCase(requestedName)) {
                    String candidateServer = candidate.getServer();
                    String requestedServer = waypoint.getServer();
                    return candidateServer != null && candidateServer.equalsIgnoreCase(requestedServer);
                }
            }
            return false;
        });
    }
}