package evernight.client.modules.movement.FakeLag;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.esp.ForcedEntityLayers;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.SidePlayer;
import evernight.client.utils.network.PacketSender;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class FakeLagModule extends Module {
    protected final Setting<Boolean> renderSetting;
    protected final Setting<Boolean> chamsSetting;
    protected final Setting<Color> fillSetting;
    protected final Setting<Color> outlineSetting;
    protected final Setting<Float> lineWidthSetting;
    final Setting<ModeChoice> modeSetting;
    private final Queue<Packet<?>> bufferedPackets = new ArrayDeque<>();
    private final Setting<Boolean> cancelSetting;
    private final Setting<Integer> timeoutSetting;
    private final Setting<Boolean> autoDisableSetting;
    private final Setting<Boolean> onDamageSetting;
    private final Setting<Boolean> onRubberBandSetting;
    private final Setting<Float> onTimeSetting;
    private final Setting<Boolean> packetsSetting;
    private final Setting<Boolean> movementSetting;
    private final Setting<Boolean> interactSetting;
    private final Setting<Boolean> swingSetting;
    private final Setting<Boolean> swapSetting;
    private final Setting<Boolean> interactEntitySetting;
    private final Setting<Boolean> diggingSetting;
    private final Setting<Boolean> pingSetting;
    private final Setting<Boolean> allSetting;
    private final ElapsedTimer lastReleaseTimer;
    private final ElapsedTimer enabledDurationTimer;
    private final ElapsedTimer pulseTimer;
    SidePlayer bufferedPlayerSnapshot;
    private AABB bufferedPositionBounds;

    public FakeLagModule() {
        super("FakeLag", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", FakeLagModule.ModeChoice.BLINK));
        this.cancelSetting = this.add(new BooleanSetting("Cancel", false));
        String timeoutName = "Timeout";
        Predicate<Object> timeoutVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.PULSE;
        this.timeoutSetting = this.add(new NumberSetting<>(timeoutName, 200, 50, 1000, timeoutVisibility).withUnit("ms"));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).markVisibilityDependency());
        this.onDamageSetting = this.add(new BooleanSetting("OnDamage", false).dependsOn(this.autoDisableSetting));
        this.onRubberBandSetting = this.add(new BooleanSetting("OnRubberBand", false).dependsOn(this.autoDisableSetting));
        NumberSetting<Float> onTimeSettingValue = new NumberSetting<>("OnTime", 0.0F, 0.0F, 5.0F);
        onTimeSettingValue.withUnit("s");
        this.onTimeSetting = this.add(onTimeSettingValue.dependsOn(this.autoDisableSetting));
        this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
        this.chamsSetting = this.add(new BooleanSetting("Chams", false).dependsOn(this.renderSetting));
        Predicate<Object> fillVisibility = ignored -> this.renderSetting.isExpanded();
        this.fillSetting = this.add(new ColorSetting("Fill", new Color(1354603007, true), fillVisibility));
        Predicate<Object> outlineVisibility = ignored -> this.renderSetting.isExpanded();
        Color outlineDefault = new Color(1186830847, true);
        this.outlineSetting = this.add(new ColorSetting("Outline", outlineDefault, outlineVisibility));
        Predicate<Object> lineWidthVisibility = ignored -> this.renderSetting.isExpanded();
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 2.0F, lineWidthVisibility));
        this.packetsSetting = this.add(new BooleanSetting("Packets", false).markAsGroup().markVisibilityDependency());
        this.movementSetting = this.add(new BooleanSetting("Movement", true).dependsOn(this.packetsSetting));
        this.interactSetting = this.add(new BooleanSetting("Interact", true).dependsOn(this.packetsSetting));
        this.swingSetting = this.add(new BooleanSetting("Swing", true).dependsOn(this.packetsSetting));
        this.swapSetting = this.add(new BooleanSetting("Swap", true).dependsOn(this.packetsSetting));
        this.interactEntitySetting = this.add(new BooleanSetting("InteractEntity", false).dependsOn(this.packetsSetting));
        this.diggingSetting = this.add(new BooleanSetting("Digging", false).dependsOn(this.packetsSetting));
        this.pingSetting = this.add(new BooleanSetting("Ping", false).dependsOn(this.packetsSetting));
        this.allSetting = this.add(new BooleanSetting("All", false).dependsOn(this.packetsSetting));
        this.lastReleaseTimer = new ElapsedTimer();
        this.enabledDurationTimer = new ElapsedTimer();
        this.pulseTimer = new ElapsedTimer();
        Setting<?> visibilitySetting = this.timeoutSetting;
        Predicate modeVisibility = ignored -> this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE;
        visibilitySetting.addVisibilityPredicate(modeVisibility);
        visibilitySetting = this.cancelSetting;
        modeVisibility = ignored -> this.modeSetting.getValue() == FakeLagModule.ModeChoice.BLINK;
        visibilitySetting.addVisibilityPredicate(modeVisibility);
        Setting<Boolean> cancelCallbackSetting = this.cancelSetting;
        Runnable cancelCallback = () -> {
            Setting<Boolean> callbackSetting = this.cancelSetting;
            if (!callbackSetting.getValue()) {
                return;
            }
            Setting<Boolean> cancelOption = this.cancelSetting;
            cancelOption.setValue(false);
            cancelOption = this.cancelSetting;
            if (!cancelOption.isVisible()) {
                return;
            }
            if (this.isWorldUnavailable()) {
                return;
            }
            if (!this.isToggled()) {
                return;
            }
            AABB rollbackBounds = this.bufferedPositionBounds;
            Vec3 rollbackPosition = BoundingBoxUtils.getBottomCenter(rollbackBounds);
            ClientAccess.evernightMinecraft.player.xo = rollbackPosition.x;
            ClientAccess.evernightMinecraft.player.yo = rollbackPosition.y;
            ClientAccess.evernightMinecraft.player.zo = rollbackPosition.z;
            ClientAccess.evernightMinecraft.player.setPos(rollbackPosition);
            ClientAccess.evernightMinecraft.player.setDeltaMovement(Vec3.ZERO);
            this.bufferedPackets.clear();
            this.disable();
        };
        cancelCallbackSetting.setChangeCallback(cancelCallback);
        EventBus eventBus = ClientAccess.eventBus;
        FakeLagRenderer renderer = new FakeLagRenderer(this);
        eventBus.subscribe(renderer);
    }

    private void releaseBufferedPackets() {
        while (!this.bufferedPackets.isEmpty()) {
            Packet<?> packet = this.bufferedPackets.poll();
            PacketSender.sendDirect(packet);
        }
        this.bufferedPositionBounds = this.captureTrackedPositionBounds();
        if (this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE) {
            this.lastReleaseTimer.reset();
        }
    }

    @Override
    public String getInfo() {
        float elapsedSeconds = (float) this.lastReleaseTimer.elapsedMillis() / 1000.0F;
        return "%.1f".formatted(Math.max(elapsedSeconds - 0.1F, 0.0F));
    }

    @Override
    public void onEnable() {
        this.lastReleaseTimer.reset();
        this.bufferedPackets.clear();
        this.bufferedPlayerSnapshot = null;
        if (this.isWorldUnavailable()) {
            this.disable();
        } else {
            this.bufferedPositionBounds = this.captureTrackedPositionBounds();
            this.enabledDurationTimer.reset();
            this.pulseTimer.reset();
        }
    }

    @Override
    public void onDisable() {
        if (!this.isWorldUnavailable()) {
            this.releaseBufferedPackets();
        }
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (this.modeSetting.getValue() == ModeChoice.PULSE
                && this.pulseTimer.hasElapsed((this.timeoutSetting.getValue()).longValue())) {
            this.releaseBufferedPackets();
            this.pulseTimer.reset();
        }
        if (this.autoDisableSetting.getValue() && !this.onTimeSetting.isAtMinimum()) {
            double durationSeconds = this.onTimeSetting.getValue();
            if (this.enabledDurationTimer.hasElapsed(durationSeconds, TimeUnit.SECONDS)) {
                this.toggle();
            }
        }
    }

    @EventHandler(priority = -200)
    private void onConnectionSendPacketEvent(PacketEvent.Send event) {
        if (this.bufferedPackets.isEmpty()) {
            this.bufferedPlayerSnapshot = new SidePlayer(ClientAccess.evernightMinecraft.player);
            ((ForcedEntityLayers) this.bufferedPlayerSnapshot).evernight$setForcedLayers(true);
            SidePlayer snapshot = this.bufferedPlayerSnapshot;
            Vec3 trackedPosition = ClientContext.playerStateTracker.getPacketPosition();
            snapshot.setPos(trackedPosition);
        }
        Packet<?> packetToClassify = event.getPacket();
        if (!this.shouldBufferPacket(packetToClassify)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        Queue<Packet<?>> packetQueue = this.bufferedPackets;
        Packet<?> packetToBuffer = event.getPacket();
        packetQueue.add(packetToBuffer);
        event.cancel();
    }

    @EventHandler
    private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        this.disable();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            if (this.modeSetting.getValue() == FakeLagModule.ModeChoice.PULSE) {
                this.pulseTimer.clear();
            }
            if (this.autoDisableSetting.getValue()) {
                if (this.onRubberBandSetting.getValue()) {
                    this.toggle();
                }
            }
        }
        if (event.getPacket() instanceof ClientboundDamageEventPacket damagePacket) {
            int damagedEntityId = damagePacket.entityId();
            if (damagedEntityId == ClientAccess.evernightMinecraft.player.getId()) {
                if (this.autoDisableSetting.getValue()) {
                    if (this.onDamageSetting.getValue()) {
                        this.toggle();
                    }
                }
            }
        }
    }

    private AABB captureTrackedPositionBounds() {
        AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        Vec3 bottomCenter = bounds.getBottomCenter();
        double originScale = -1.0D;
        Vec3 originOffset = bottomCenter.scale(originScale);
        bounds = bounds.move(originOffset);
        Vec3 trackedPosition = ClientContext.playerStateTracker.getPacketPosition();
        return bounds.move(trackedPosition);
    }

    private boolean shouldBufferPacket(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket && this.movementSetting.getValue()
                || packet instanceof ServerboundUseItemOnPacket && this.interactSetting.getValue()
                || packet instanceof ServerboundInteractPacket && this.interactEntitySetting.getValue()
                || packet instanceof ServerboundPlayerActionPacket && this.diggingSetting.getValue()
                || packet instanceof ServerboundSwingPacket && this.swingSetting.getValue()
                || packet instanceof ServerboundSetCarriedItemPacket && this.swapSetting.getValue()
                || (packet instanceof ServerboundPongPacket || packet instanceof ServerboundKeepAlivePacket
                || packet instanceof ServerboundResourcePackPacket
                || packet instanceof ServerboundPingRequestPacket) && this.pingSetting.getValue()
                || this.allSetting.getValue();
    }

    public enum ModeChoice implements NamedEntry {
        BLINK("Blink"),
        PULSE("Pulse");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}