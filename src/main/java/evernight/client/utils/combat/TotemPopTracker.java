package evernight.client.utils.combat;

import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.bus.EventBus;
import evernight.client.utils.entity.EntityUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.ConnectScreenConnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.PlayerTotemEvent;

public final class TotemPopTracker implements ClientAccess {
    private final Map popCounts = new ConcurrentHashMap();
    private final Map disconnectTimes = new ConcurrentHashMap();

    public TotemPopTracker() {
        ClientAccess.eventBus.subscribe(this);
    }

    public int getPopCount(Player player) {
        GameProfile profile = player.getGameProfile();
        return this.getPopCount(profile.name());
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        Iterator players = ClientAccess.evernightMinecraft.level.players().iterator();
        while (players.hasNext()) {
            Player player = (Player) players.next();
            if (player != null) {
                if (!(EntityUtils.getHealthWithAbsorption(player) > 0.0F)) {
                    Component playerName = player.getName();
                    String name = playerName.getString();
                    if (this.popCounts.containsKey(name)) {
                        int count = (Integer) this.popCounts.get(name);
                        this.popCounts.remove(name);
                        EventBus eventBus = ClientAccess.eventBus;
                        PlayerTotemEvent deathEvent = new PlayerTotemEvent(player, count, PlayerTotemEvent.Kind.DEATH);
                        eventBus.post(deathEvent);
                    }
                }
            }
        }
        String lastExpiredName = null;
        Iterator disconnectIterator = this.disconnectTimes.entrySet().iterator();
        while (disconnectIterator.hasNext()) {
            Entry entry = (Entry) disconnectIterator.next();
            long expiresAt = (Long) entry.getValue();
            expiresAt += 120000L;
            if (expiresAt < System.currentTimeMillis()) {
                lastExpiredName = (String) entry.getKey();
                this.popCounts.remove(entry.getKey());
            }
        }
        if (lastExpiredName != null) {
            this.disconnectTimes.remove(lastExpiredName);
        }
    }

    @EventHandler
    private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
        if (ClientContext.serverStateManager.getServerData() != null) {
            String address = event.getServerAddress();
            if (!address.equals(ClientContext.serverStateManager.getServerData().ip)) {
                return;
            }
        }
        this.popCounts.clear();
    }

    public int getPopCount(String name) {
        return (Integer) this.popCounts.getOrDefault(name, Integer.valueOf(0));
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> incomingPacket = event.getPacket();
        if (ClientAccess.evernightMinecraft.isSameThread()) {
            this.handleIncomingPacket(incomingPacket);
            return;
        }
        // PacketEvent.Receive is posted on the netty thread; the client world and entity table are not safe there.
        ClientAccess.evernightMinecraft.execute(() -> this.handleIncomingPacket(incomingPacket));
    }

    private void handleIncomingPacket(Packet<?> incomingPacket) {
        if (!this.isWorldUnavailable()) {
            if (incomingPacket instanceof ClientboundEntityEventPacket entityEventPacket) {
                byte eventId = entityEventPacket.getEventId();
                if (eventId == 35) {
                    if (entityEventPacket.getEntity(ClientAccess.evernightMinecraft.level) instanceof Player player) {
                        String playerName = player.getGameProfile().name();
                        Map counts = this.popCounts;
                        BiFunction<String, Integer, Integer> incrementCount =
                                (ignoredKey, previousCount) -> previousCount == null ? 1 : previousCount + 1;
                        counts.compute(playerName, incrementCount);
                        EventBus eventBus = ClientAccess.eventBus;
                        PlayerTotemEvent popEvent = new PlayerTotemEvent(player, (Integer) this.popCounts.get(playerName), PlayerTotemEvent.Kind.TOTEM_POP);
                        eventBus.post(popEvent);
                    }
                }
            }
            if (incomingPacket instanceof ClientboundPlayerInfoUpdatePacket playerInfoUpdatePacket) {
                Iterator joinedEntries = playerInfoUpdatePacket.newEntries().iterator();
                while (joinedEntries.hasNext()) {
                    ClientboundPlayerInfoUpdatePacket.Entry joinedEntry = (ClientboundPlayerInfoUpdatePacket.Entry) joinedEntries.next();
                    Map joinedDisconnectTimes = this.disconnectTimes;
                    GameProfile joinedProfile = joinedEntry.profile();
                    String joinedName = joinedProfile.name();
                    joinedDisconnectTimes.remove(joinedName);
                }
            }
            if (incomingPacket instanceof ClientboundPlayerInfoRemovePacket(java.util.List<UUID> profileIds)) {
                Iterator removedProfiles = profileIds.iterator();
                while (removedProfiles.hasNext()) {
                    UUID removedId = (UUID) removedProfiles.next();
                    ClientPacketListener listener = ClientAccess.evernightMinecraft.player.connection;
                    PlayerInfo playerInfo = listener.getPlayerInfo(removedId);
                    if (playerInfo != null) {
                        Map removedDisconnectTimes = this.disconnectTimes;
                        GameProfile removedProfile = playerInfo.getProfile();
                        String removedName = removedProfile.name();
                        long disconnectedAt = System.currentTimeMillis();
                        removedDisconnectTimes.put(removedName, disconnectedAt);
                    }
                }
            }
        }
    }
}