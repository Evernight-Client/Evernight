package evernight.client.managers.world;

import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.ConnectScreenConnectEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.exploit.FastLatency.FastLatencyModule;
import evernight.client.settings.options.DimensionType;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import evernight.client.managers.registry.ModuleRegistry;

public final class ServerStateManager implements ClientAccess {
    private static final Pattern LATENCY_PATTERN;
    private static FastLatencyModule fastLatency;

    static {
        fastLatency = ClientContext.getModule(FastLatencyModule.class);
        LATENCY_PATTERN = Pattern.compile("\\d+ ping");
    }

    private final ArrayDeque<Float> tickRateSamples;
    private final ElapsedTimer setbackTimer;
    private final ElapsedTimer teleportAckTimer;
    private final ElapsedTimer packetFlowTimer;
    private final ElapsedTimer peakLatencyTimer;
    public boolean autoLogged;
    private float latestTickRate;
    private float averageTickRate;
    private long lastPacketMillis;
    private long lastEntityPacketMillis;
    private long lastTimeUpdateMillis;
    private int teleportId;
    private boolean serverSprinting;
    private boolean serverSneaking;
    private boolean inEnd;
    private ServerData serverData;
    private int footerLatencyMillis;
    private int peakLatencyMillis;

    public ServerStateManager() {
        this.tickRateSamples = new ArrayDeque<>(20);
        this.setbackTimer = new ElapsedTimer();
        this.teleportAckTimer = new ElapsedTimer();
        this.packetFlowTimer = new ElapsedTimer();
        this.peakLatencyTimer = new ElapsedTimer();
        ClientAccess.eventBus.subscribe(this);
    }

    public static void bindModules(ModuleRegistry registry) {
        fastLatency = registry.getModule(FastLatencyModule.class);
    }

    public boolean isServerSneaking() {
        return this.serverSneaking;
    }

    public int getTeleportId() {
        return this.teleportId;
    }

    public ElapsedTimer getTeleportAckTimer() {
        return this.teleportAckTimer;
    }

    public float getTickRate() {
        return this.averageTickRate;
    }

    public long getLastPacketTime() {
        return this.getLastPacketTime(PacketActivity.ANY);
    }

    public boolean isServerSprinting() {
        return this.serverSprinting;
    }

    public int getPlayerLatencyMillis(Player player) {
        ClientPacketListener capturedConnection = ClientAccess.evernightMinecraft.player.connection;
        GameProfile profile = player.getGameProfile();
        UUID profileId = profile.id();
        PlayerInfo playerInfo = capturedConnection.getPlayerInfo(profileId);
        return playerInfo == null ? 0 : playerInfo.getLatency();
    }

    public float getLatestTickRate() {
        return this.latestTickRate;
    }

    public ElapsedTimer getSetbackTimer() {
        return this.setbackTimer;
    }

    public int getLatencyMillis() {
        if (ClientAccess.evernightMinecraft.player == null) {
            return 0;
        }
        FastLatencyModule fastLatencyModule = fastLatency;
        if (fastLatencyModule.isToggled()) {
            return fastLatency.getLatencyMillis();
        }
        LocalPlayer latencyPlayer = ClientAccess.evernightMinecraft.player;
        int playerLatency = this.getPlayerLatencyMillis(latencyPlayer);
        return playerLatency == 0 ? this.footerLatencyMillis : playerLatency;
    }

    public int getLatencyTicks() {
        return (int) Math.ceil(this.getLatencyMillis() / 50.0F);
    }

    public int getPeakLatencyMillis() {
        return this.peakLatencyMillis;
    }

    public ServerData getServerData() {
        return this.serverData;
    }

    public void setServerData(ServerData data) {
        this.serverData = data;
    }

    public boolean hasElapsedSinceSetback(long durationMillis) {
        ElapsedTimer timer = this.getSetbackTimer();
        return timer.hasElapsed(durationMillis);
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        long packetAgeMillis = System.currentTimeMillis() - this.lastPacketMillis;
        if (packetAgeMillis > 500L) {
            this.packetFlowTimer.reset();
        }
        int latencyMillis = this.getLatencyMillis();
        if (this.peakLatencyTimer.hasElapsed(10.0D, TimeUnit.SECONDS) || latencyMillis > this.peakLatencyMillis) {
            this.peakLatencyMillis = latencyMillis;
            this.peakLatencyTimer.reset();
        }
        DimensionType dimension = WorldUtils.getDimension();
        this.inEnd = dimension.isEnd();
    }

    public long getLastPacketTime(ServerStateManager.PacketActivity activity) {
        return switch (activity) {
            case ANY -> this.lastPacketMillis;
            case ENTITY -> this.lastEntityPacketMillis;
        };
    }

    @EventHandler(priority = 200)
    private void onConnectionSendPacketEvent(PacketEvent.Send event) {
        Input extractedInput;
        if (event.getPacket() instanceof ServerboundPlayerCommandPacket commandPacket) {
            if (commandPacket.getAction() == Action.START_SPRINTING) {
                if (this.serverSprinting) {
                    event.cancel();
                }
                this.serverSprinting = true;
            }
            if (commandPacket.getAction() == Action.STOP_SPRINTING) {
                if (!this.serverSprinting) {
                    event.cancel();
                }
                this.serverSprinting = false;
            }
        }
        if (event.getPacket() instanceof ServerboundPlayerInputPacket(Input input)) {
            try {
                extractedInput = input;
            } catch (Throwable inputFailure) {
                throw new MatchException(inputFailure.toString(), inputFailure);
            }
            this.serverSneaking = extractedInput.shift();
        }
        if (event.getPacket() instanceof ServerboundAcceptTeleportationPacket) {
            this.teleportAckTimer.reset();
        }
    }

    public boolean hasStablePacketFlowFor(long durationMillis) {
        return this.packetFlowTimer.hasElapsed(durationMillis);
    }

    @EventHandler
    private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
        this.autoLogged = false;
        this.footerLatencyMillis = 0;
        this.peakLatencyMillis = 0;
        this.peakLatencyTimer.setTime(-1L);
        // Tick rate samples belong to the previous server; a stale lastTimeUpdateMillis would make the
        // first sample of the new session wildly inaccurate.
        this.tickRateSamples.clear();
        this.latestTickRate = 0.0F;
        this.averageTickRate = 0.0F;
        this.lastTimeUpdateMillis = 0L;
    }

    private void recordReceivedPacket(Packet packet) {
        if (packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundRotateHeadPacket
                || this.inEnd) {
            this.lastEntityPacketMillis = System.currentTimeMillis();
        }
        this.lastPacketMillis = System.currentTimeMillis();
    }

    public float getTickRateScale() {
        float scale = this.averageTickRate / 20.0F;
        // 20 TPS is assumed until the first two time packets provide a real sample; returning 0 would
        // make every "delay / scale" caller divide by zero and turn the delay into Long.MAX_VALUE.
        return scale <= 0.0F ? 1.0F : scale;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet receivedPacket = event.getPacket();
        this.recordReceivedPacket(receivedPacket);
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket) {
            this.teleportId = positionPacket.id();
            ElapsedTimer ackTimer = this.teleportAckTimer;
            if (ackTimer.hasElapsed(50L)) {
                this.setbackTimer.reset();
            }
            LocalPlayer sprintPlayer = ClientAccess.evernightMinecraft.player;
            this.serverSprinting = sprintPlayer.isSprinting();
        }
        if (event.getPacket() instanceof ClientboundSetTimePacket) {
            if (this.lastTimeUpdateMillis != 0L) {
                int sampleCount = this.tickRateSamples.size();
                if (sampleCount > (20)) {
                    this.tickRateSamples.poll();
                }
                float maxRate = 2e+01F;
                float rateScale = 2e+01F;
                float periodScale = 1e+03F;
                float measuredTickRate = rateScale * (periodScale
                        / (float) (System.currentTimeMillis() - this.lastTimeUpdateMillis));
                float upperClampedRate = Math.min(maxRate, measuredTickRate);
                this.latestTickRate = Math.max(0.0F, upperClampedRate);
                ArrayDeque<Float> samples = this.tickRateSamples;
                float capturedRate = this.latestTickRate;
                samples.add(capturedRate);
                float rateSum = 0.0F;
                Iterator<Float> samplesIterator = this.tickRateSamples.iterator();
                while (samplesIterator.hasNext()) {
                    Float sample = samplesIterator.next();
                    float sampleMax = 2e+01F;
                    float sampleRate = sample;
                    float upperClampedSample = Math.min(sampleMax, sampleRate);
                    rateSum += Math.max(0.0F, upperClampedSample);
                }
                if (!this.tickRateSamples.isEmpty()) {
                    rateSum /= this.tickRateSamples.size();
                }
                double averageAsDouble = rateSum;
                float averagedRate;
                if (averageAsDouble > 19.95D) {
                    averagedRate = 2e+01F;
                } else {
                    averagedRate = rateSum;
                }
                this.averageTickRate = averagedRate;
            }
            this.lastTimeUpdateMillis = System.currentTimeMillis();
        }
        if (event.getPacket() instanceof ClientboundBundlePacket bundlePacket) {
            for (Packet<?> subpacket : bundlePacket.subPackets()) {
                EventBus eventBus = ClientAccess.eventBus;
                PacketEvent.Receive subpacketEvent = new PacketEvent.Receive(subpacket);
                eventBus.post(subpacketEvent);
            }
        }
        if (event.getPacket() instanceof ClientboundTabListPacket tabListPacket) {
            ClientPacketListener serverConnection = ClientAccess.evernightMinecraft.player.connection;
            ServerData connectedServer = serverConnection.getServerData();
            if (connectedServer == null) {
                return;
            }
            Component footerComponent = tabListPacket.footer();
            String footer = footerComponent.getString();
            Matcher latencyMatcher = LATENCY_PATTERN.matcher(footer);
            if (latencyMatcher.find()) {
                String latencyText = latencyMatcher.group();
                try {
                    int textLength = latencyText.length();
                    int suffixEnd = textLength - 5;
                    latencyText = latencyText.substring(0, suffixEnd).trim();
                    this.footerLatencyMillis = Integer.parseInt(latencyText);
                } catch (Throwable parseFailure) {
                }
            }
        }
    }

    public enum PacketActivity {
        ANY, ENTITY
    }
}