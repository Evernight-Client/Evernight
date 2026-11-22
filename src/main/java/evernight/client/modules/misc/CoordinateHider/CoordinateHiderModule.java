package evernight.client.modules.misc.CoordinateHider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientContext;
import evernight.client.events.network.ClientPacketListenerSendChatEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.world.CoordinateCensor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Hides coordinates on the client and blocks outgoing chat that would reveal them.
 *
 * <p>Every redaction is display-only: client state, packets and server data are left untouched,
 * except for outgoing chat which is cancelled before it is sent.
 */
public final class CoordinateHiderModule extends Module {
    private static final String XAERO_MINIMAP = "xaerominimap";
    private static final String XAERO_WORLD_MAP = "xaeroworldmap";

    private final BooleanSetting minimapGroupSetting;
    private final BooleanSetting minimapCoordsSetting;
    private final BooleanSetting minimapWaypointsSetting;
    private final BooleanSetting worldMapCoordsSetting;
    private final BooleanSetting worldMapWaypointsSetting;
    private final BooleanSetting clientGroupSetting;
    private final BooleanSetting f3CoordinatesSetting;
    private final BooleanSetting hudCoordinatesSetting;
    private final BooleanSetting clientWaypointsSetting;
    private final BooleanSetting chatGroupSetting;
    private final BooleanSetting outgoingCoordinatesSetting;
    private final BooleanSetting outgoingBase64Setting;
    private final BooleanSetting baritoneGroupSetting;
    private final BooleanSetting hideBaritoneCoordsSetting;
    private boolean configLoaded;
    private boolean hasNewBaritoneSetting;

    public CoordinateHiderModule() {
        super("CoordinateHider",
                ModuleCategory.MISC, "coordhider");

        BooleanSetting minimapGroup = new BooleanSetting("Minimap", true);
        minimapGroup.markAsGroup();
        minimapGroup.addVisibilityPredicate(ignored -> isAnyXaeroLoaded());
        this.minimapGroupSetting = this.add(minimapGroup);

        BooleanSetting minimapCoords = new BooleanSetting("MinimapCoords", true);
        minimapCoords.dependsOn(this.minimapGroupSetting);
        minimapCoords.addVisibilityPredicate(ignored -> isModLoaded(XAERO_MINIMAP));
        this.minimapCoordsSetting = this.add(minimapCoords);

        BooleanSetting minimapWaypoints = new BooleanSetting("MinimapWaypoints", true);
        minimapWaypoints.dependsOn(this.minimapGroupSetting);
        minimapWaypoints.addVisibilityPredicate(ignored -> isModLoaded(XAERO_MINIMAP));
        this.minimapWaypointsSetting = this.add(minimapWaypoints);

        BooleanSetting worldMapCoords = new BooleanSetting("WorldMapCoords", true);
        worldMapCoords.dependsOn(this.minimapGroupSetting);
        worldMapCoords.addVisibilityPredicate(ignored -> isModLoaded(XAERO_WORLD_MAP));
        this.worldMapCoordsSetting = this.add(worldMapCoords);

        BooleanSetting worldMapWaypoints = new BooleanSetting("WorldMapWaypoints", true);
        worldMapWaypoints.dependsOn(this.minimapGroupSetting);
        worldMapWaypoints.addVisibilityPredicate(ignored -> isModLoaded(XAERO_WORLD_MAP));
        this.worldMapWaypointsSetting = this.add(worldMapWaypoints);

        BooleanSetting clientGroup = new BooleanSetting("ClientSurfaces", true);
        clientGroup.markAsGroup();
        this.clientGroupSetting = this.add(clientGroup);

        BooleanSetting f3Coordinates = new BooleanSetting("F3Coordinates", true);
        f3Coordinates.dependsOn(this.clientGroupSetting);
        this.f3CoordinatesSetting = this.add(f3Coordinates);

        BooleanSetting hudCoordinates = new BooleanSetting("HudCoordinates", true);
        hudCoordinates.dependsOn(this.clientGroupSetting);
        this.hudCoordinatesSetting = this.add(hudCoordinates);

        BooleanSetting clientWaypoints = new BooleanSetting("ClientWaypoints", true);
        clientWaypoints.dependsOn(this.clientGroupSetting);
        this.clientWaypointsSetting = this.add(clientWaypoints);

        BooleanSetting chatGroup = new BooleanSetting("ChatInterception", true);
        chatGroup.markAsGroup();
        this.chatGroupSetting = this.add(chatGroup);

        BooleanSetting outgoingCoordinates = new BooleanSetting("OutgoingCoordinates", true);
        outgoingCoordinates.dependsOn(this.chatGroupSetting);
        this.outgoingCoordinatesSetting = this.add(outgoingCoordinates);

        BooleanSetting outgoingBase64 = new BooleanSetting("OutgoingBase64", true);
        outgoingBase64.dependsOn(this.chatGroupSetting);
        this.outgoingBase64Setting = this.add(outgoingBase64);

        BooleanSetting baritoneGroup = new BooleanSetting("BaritoneOptions", true);
        baritoneGroup.markAsGroup();
        baritoneGroup.addVisibilityPredicate(ignored -> isModLoaded("baritone"));
        this.baritoneGroupSetting = this.add(baritoneGroup);

        BooleanSetting hideBaritoneCoords = new BooleanSetting("HideBaritoneCoords", false);
        hideBaritoneCoords.dependsOn(this.baritoneGroupSetting);
        hideBaritoneCoords.addVisibilityPredicate(ignored -> isModLoaded("baritone"));
        this.hideBaritoneCoordsSetting = this.add(hideBaritoneCoords);
    }

    public static boolean shouldHideMinimapCoords() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.minimapCoordsSetting.getValue();
    }

    public static boolean shouldHideMinimapWaypoints() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.minimapWaypointsSetting.getValue();
    }

    public static boolean shouldHideWorldMapCoords() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.worldMapCoordsSetting.getValue();
    }

    public static boolean shouldHideWorldMapWaypoints() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.worldMapWaypointsSetting.getValue();
    }

    public static boolean shouldHideDebugScreen() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.f3CoordinatesSetting.getValue();
    }

    public static boolean shouldHideHudCoordinates() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.hudCoordinatesSetting.getValue();
    }

    public static boolean shouldHideClientWaypoints() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.clientWaypointsSetting.getValue();
    }

    public static boolean shouldCensorBaritone() {
        CoordinateHiderModule module = getInstance();
        return module != null && module.isToggled() && module.hideBaritoneCoordsSetting.getValue();
    }

    /**
     * Moves the old {@code Baritone.CensorCoords} value into this module when no new value exists.
     */
    public static void applyLegacyBaritoneCensor(boolean value) {
        CoordinateHiderModule module = getInstance();
        if (module == null || (module.configLoaded && module.hasNewBaritoneSetting)) {
            return;
        }
        module.hideBaritoneCoordsSetting.setValue(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        this.hasNewBaritoneSetting = hasSetting(json, "HideBaritoneCoords");
        super.fromJson(json);
        this.configLoaded = true;
    }

    @EventHandler(priority = 200)
    private void onOutgoingChat(ClientPacketListenerSendChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        boolean base64Blocked = this.outgoingBase64Setting.getValue() && CoordinateCensor.containsBase64(message);
        boolean coordinatesBlocked = this.outgoingCoordinatesSetting.getValue() && CoordinateCensor.containsCoordinate(message);
        if (!base64Blocked && !coordinatesBlocked) {
            return;
        }
        event.cancel();
        if (base64Blocked) {
            ChatUtils.printChat(Component.literal(Translations.ui("modules.CoordinateHider.OutgoingBase64Blocked")), ChatUtils.moduleSignature(this),
                    ChatUtils.MessagePriority.HIGH);
        } else {
            ChatUtils.printChat(Component.literal(Translations.ui("modules.CoordinateHider.OutgoingCoordinatesBlocked")), ChatUtils.moduleSignature(this),
                    ChatUtils.MessagePriority.HIGH);
        }
    }

    private static CoordinateHiderModule getInstance() {
        return ClientContext.getModule(CoordinateHiderModule.class);
    }

    private static boolean isAnyXaeroLoaded() {
        return isModLoaded(XAERO_MINIMAP) || isModLoaded(XAERO_WORLD_MAP);
    }

    private static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    private static boolean hasSetting(JsonElement json, String configName) {
        return json instanceof JsonObject moduleJson
                && moduleJson.get("settings") instanceof JsonObject settingsJson
                && settingsJson.has(configName);
    }
}
