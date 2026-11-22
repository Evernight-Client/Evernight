package evernight.client.utils.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.render.font.FontRenderer;
import evernight.client.utils.core.CompressionUtils;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.FileUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.ConnectScreenConnectEvent;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerTickEvent;

public final class MapDataCache implements ClientAccess {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final ElapsedTimer saveTimer = new ElapsedTimer();
    private final Map<String, MapItemSavedData> mapDataByName = Collections.synchronizedMap(new HashMap<>());
    private String serverAddress = null;

    public MapDataCache() {
        ClientAccess.eventBus.subscribe(this);
    }

    public void load(String address) {
        this.mapDataByName.clear();
        Path filePath = ConfigurationManager.MAPS_DIRECTORY.resolve(FontRenderer.sanitizeFileName(address) + ".data");
        if (!filePath.toFile().exists()) {
            return;
        }
        byte[] decodedBytes;
        try {
            byte[] fileBytes = FileUtils.readBytes(filePath);
            decodedBytes = CompressionUtils.inflate(fileBytes);
        } catch (Exception decodeFailure) {
            return;
        }
        ByteArrayInputStream byteInput = new ByteArrayInputStream(decodedBytes);
        try (DataInputStream input = new DataInputStream(byteInput)) {
            int entryCount = input.readInt();
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                String name = input.readUTF();
                byte scale = input.readByte();
                MapItemSavedData mapData = MapItemSavedData.createForClient(scale, true, null);
                for (int colorIndex = 0;
                     colorIndex < 16384;
                     colorIndex++) {
                    mapData.colors[colorIndex] = input.readByte();
                }
                this.mapDataByName.put(name, mapData);
            }
        } catch (Exception loadFailure) {
            LOGGER.warn("Could not load map data from {}", filePath, loadFailure);
        }
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (this.saveTimer.hasElapsedAndReset(5L, TimeUnit.MINUTES) && this.serverAddress != null) {
            CompletableFuture.runAsync(() -> {
                String address = this.serverAddress;
                this.save(address);
            }, ClientAccess.executorService);
        }
    }

    @EventHandler
    private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
        this.serverAddress = event.getServerAddress();
        this.load(this.serverAddress);
    }

    @EventHandler
    private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        if (this.serverAddress != null) {
            this.save(this.serverAddress);
        }
    }

    public MapItemSavedData getMapData(ItemStack stack, MapId mapId) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        MapItemSavedData worldData = MapItem.getSavedData(mapId, level);
        if (mapId.id() != -1 && worldData != null) {
            return worldData;
        }
        String name = stack.getHoverName().getString();
        return stack.has(DataComponents.CUSTOM_NAME) ? this.mapDataByName.getOrDefault(name, null) : null;
    }

    public void save(String address) {
        if (this.mapDataByName.isEmpty()) {
            return;
        }
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(byteOutput)) {
            HashSet<Entry<String, MapItemSavedData>> entrySnapshot;
            synchronized (this.mapDataByName) {
                entrySnapshot = new HashSet<>(this.mapDataByName.entrySet());
            }
            int entryCount = entrySnapshot.size();
            output.writeInt(entryCount);
            for (Entry<String, MapItemSavedData> entry : entrySnapshot) {
                String name = entry.getKey();
                output.writeUTF(name);
                byte scale = entry.getValue().scale;
                output.writeByte(scale);
                for (byte color : entry.getValue().colors) {
                    output.writeByte(color);
                }
            }
            Path filePath = ConfigurationManager.MAPS_DIRECTORY.resolve(FontRenderer.sanitizeFileName(address) + ".data");
            byte[] serializedBytes = byteOutput.toByteArray();
            FileUtils.writeBytes(filePath, CompressionUtils.deflate(serializedBytes, 2));
        } catch (Exception saveFailure) {
            LOGGER.warn("Could not save map data for {}", address, saveFailure);
        }
    }

    public MapItemSavedData getMapData(ItemStack stack, int mapId) {
        MapId lookupId = new MapId(mapId);
        return this.getMapData(stack, lookupId);
    }

    private void cacheNamedMap(ItemStack stack) {
        if (stack.getItem() == Items.FILLED_MAP && stack.has(DataComponents.CUSTOM_NAME)) {
            String name = stack.getHoverName().getString();
            MapId mapId = stack.getOrDefault(DataComponents.MAP_ID, new MapId(-1));
            this.mapDataByName.compute(name, (ignoredName, previousData) -> {
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                return MapItem.getSavedData(mapId, level);
            });
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundMapItemDataPacket) {
            TaskScheduler scheduler = ClientContext.taskScheduler;
            Runnable updateMaps = () -> {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                if (player == null) {
                    return;
                }
                Inventory inventory = player.getInventory();
                for (Object item : inventory.getNonEquipmentItems()) {
                    ItemStack stack = (ItemStack) item;
                    this.cacheNamedMap(stack);
                }
            };
            scheduler.scheduleRotationTask(updateMaps, 1);
        }
    }
}