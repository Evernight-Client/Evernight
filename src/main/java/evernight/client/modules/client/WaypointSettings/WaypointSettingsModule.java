package evernight.client.modules.client.WaypointSettings;

import evernight.client.ClientContext;
import evernight.client.integrations.xaero.WaypointSync;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PermanentModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.ServerKeyMode;

/**
 * Client settings for the waypoint bridge and for how waypoints are grouped per server.
 */
public class WaypointSettingsModule extends PermanentModule {
    private final Setting<Boolean> waypointSyncSetting;
    private final Setting<ServerKeyMode> serverKeyModeSetting;

    public WaypointSettingsModule() {
        super("WaypointSettings", ModuleCategory.CLIENT);
        BooleanSetting syncDefinition = new BooleanSetting(("WaypointSync"), false);
        syncDefinition.addVisibilityPredicate(ignoredValue -> ClientContext.waypointAdapter.isAvailable());
        this.waypointSyncSetting = this.add(syncDefinition);
        this.serverKeyModeSetting = this.add(new EnumSetting<>(("ServerKeyMode"), ServerKeyMode.ADDRESS));
        this.waypointSyncSetting.setChangeCallback(() -> {
            WaypointSync waypointSync = ClientContext.waypointSync;
            if (waypointSync != null) {
                waypointSync.setEnabled(this.waypointSyncSetting.getValue());
            }
        });
    }

    public ServerKeyMode getServerKeyMode() {
        return this.serverKeyModeSetting.getValue();
    }
}
