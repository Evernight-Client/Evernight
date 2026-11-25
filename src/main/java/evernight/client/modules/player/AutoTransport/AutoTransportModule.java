package evernight.client.modules.player.AutoTransport;

import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

/**
 * Automates the pearl transport loop between two bases.
 *
 * <p>The player is pulled between a source end (inventory gets loaded) and a destination end
 * (inventory gets unloaded) by ender pearls. Cargo is moved as shulker boxes; chests are identified by
 * the item displayed in the item frame attached to them, and ender chests are already placed at both
 * ends. Before throwing, the module finds the nearest bubble column, sneaks to its edge without
 * stepping on it and throws the pearl down into it.
 *
 * <p>The two ends are far apart, so the module tells them apart by the nearby anchor frame: redstone
 * dust renamed to 搬走 marks the source end, redstone block renamed to 搬到 marks the destination end.
 *
 * <p>Everything is client side: the module only sends the interactions a player would send, and it
 * pauses with a translated chat message when a required chest, bubble column or pearl is missing.
 */
public final class AutoTransportModule extends Module {
    private static final String TRANSLATION_PREFIX = "modules.AutoTransport.";

    private final Setting<AutoTransportSideChoice> sideChoiceSetting;
    private final Setting<Float> scanRangeSetting;
    private final Setting<Float> bubbleRadiusSetting;
    private final Setting<Integer> throwDelaySetting;
    private final Setting<Integer> clickDelaySetting;
    private final Setting<Double> teleportDistanceSetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<String> sourceRequestCommandSetting;
    private final Setting<String> destinationRequestCommandSetting;
    private final Setting<Boolean> notifySetting;
    private final Setting<Boolean> debugSetting;
    private final AutoTransportController controller;

    public AutoTransportModule() {
        super("AutoTransport", ModuleCategory.AUTO, "autotransport", "haul");
        this.sideChoiceSetting = this.add(new EnumSetting<>("Side", AutoTransportSideChoice.AUTO));
        this.scanRangeSetting = this.add(new NumberSetting<>("ScanRange", 16.0F, 4.0F, 32.0F).withUnit("m"));
        this.bubbleRadiusSetting = this.add(new NumberSetting<>("BubbleRadius", 8.0F, 2.0F, 16.0F).withUnit("m"));
        this.throwDelaySetting = this.add(new NumberSetting<>("ThrowDelay", 300, 0, 5000).withUnit("ms"));
        this.clickDelaySetting = this.add(new NumberSetting<>("ClickDelay", 150, 50, 1000).withUnit("ms"));
        this.teleportDistanceSetting =
                this.add(new NumberSetting<>("TeleportDistance", 8.0D, 2.0D, 64.0D).withUnit("m"));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
        this.sourceRequestCommandSetting = this.add(new StringSetting("SourceRequestCommand", ""));
        this.destinationRequestCommandSetting = this.add(new StringSetting("DestinationRequestCommand", ""));
        this.notifySetting = this.add(new BooleanSetting("Notify", true));
        this.debugSetting = this.add(new BooleanSetting("Debug", false));
        this.controller = new AutoTransportController(this);
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable()) {
            this.disable();
            return;
        }
        if (!ClientContext.baritoneBridge.isAvailable()) {
            this.notifyError("NoBaritone");
        }
        this.controller.start();
    }

    @Override
    public void onDisable() {
        this.controller.shutdown();
    }

    @Override
    public String getInfo() {
        return this.isToggled() ? this.controller.getPhaseName() : null;
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerTickEvent event) {
        this.controller.tick();
    }

    public AutoTransportSideChoice getSideChoice() {
        return this.sideChoiceSetting.getValue();
    }

    public double getScanRange() {
        return this.scanRangeSetting.getValue();
    }

    public double getBubbleRadius() {
        return this.bubbleRadiusSetting.getValue();
    }

    public long getThrowDelay() {
        return this.throwDelaySetting.getValue();
    }

    public long getClickDelay() {
        return this.clickDelaySetting.getValue();
    }

    public double getTeleportDistance() {
        return this.teleportDistanceSetting.getValue();
    }

    public boolean isRotate() {
        return this.rotateSetting.getValue();
    }

    public String getSourceRequestCommand() {
        return this.sourceRequestCommandSetting.getValue();
    }

    public String getDestinationRequestCommand() {
        return this.destinationRequestCommandSetting.getValue();
    }

    public boolean isDebug() {
        return this.debugSetting.getValue();
    }

    public void notifyInfo(String key, Object... args) {
        if (this.notifySetting == null || !this.notifySetting.getValue()) {
            return;
        }
        this.notifyAlways(key, args);
    }

    public void notifyError(String key, Object... args) {
        this.notifyAlways(key, args);
    }

    public void notifyAlways(String key, Object... args) {
        String message = Translations.format(TRANSLATION_PREFIX + key, args);
        ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this));
    }
}
