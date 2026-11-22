package evernight.client.integrations.xaero;

/**
 * Controls the optional waypoint bridge without referencing any Xaero type, so the client settings
 * can toggle it even when the minimap mod is not installed.
 */
public interface WaypointSync {
    void setEnabled(boolean enabled);

    boolean isEnabled();
}
