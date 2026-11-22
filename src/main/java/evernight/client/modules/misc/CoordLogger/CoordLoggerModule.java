package evernight.client.modules.misc.CoordLogger;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.DimensionType;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CoordLoggerModule extends Module {
    private static final DateTimeFormatter timestampFormatter;

    static {
        timestampFormatter = DateTimeFormatter.ofPattern(("HH:mm dd-MM-yyyy"));
    }

    private final Setting<Boolean> disconnectSetting;
    private final Setting<Boolean> deathSetting;
    private final Setting<Boolean> fileSetting;
    private volatile String coordinateText;

    public CoordLoggerModule() {
        super("CoordLogger", ModuleCategory.MISC);
        this.disconnectSetting = this.add(new BooleanSetting("Log", true));
        this.deathSetting = this.add(new BooleanSetting("Death", true));
        this.fileSetting = this.add(new BooleanSetting("File", false));
    }

    private void appendRetainedEntry(String entry) {
        Path directory = ConfigurationManager.ROOT_DIRECTORY;
        Path logFile = directory.resolve("coordlogger.txt");
        try {
            Stream<String> existingLines;
            LinkOption[] existenceOptions = new LinkOption[0];
            if (Files.exists(logFile, existenceOptions)) {
                existingLines = Files.lines(logFile);
            } else {
                existingLines = Stream.empty();
            }
            try (existingLines) {
                Stream<String> filteredLines = Stream.concat(existingLines, Stream.of(entry)).filter(this::isRetainedEntry);
                List<String> retainedLines = filteredLines.collect(Collectors.toList());
                OpenOption[] writeOptions = new OpenOption[0];
                Files.write(logFile, retainedLines, writeOptions);
            }
        } catch (IOException ignoredIOException) {
        }
    }

    private boolean isRetainedEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String[] fields = entry.split(("\\|"));
        int fieldCount = fields.length;
        if (fieldCount != (3)) {
            return false;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            String timestampText = fields[2];
            String timestampSnapshot = timestampText.trim();
            DateTimeFormatter parserFormatter = timestampFormatter;
            LocalDateTime entryTime = LocalDateTime.parse(timestampSnapshot, parserFormatter);
            ChronoUnit ageUnit = ChronoUnit.DAYS;
            long ageDays = ageUnit.between(entryTime, now);
            return ageDays < 7L;
        } catch (DateTimeParseException invalidTimestamp) {
            return false;
        }
    }

    private void saveCoordinates(String reason) {
        if (this.coordinateText != null) {
            StringBuilder entry = new StringBuilder().append(this.coordinateText).append(" | ").append(reason);
            KeyboardHandler keyboard = ClientAccess.evernightMinecraft.keyboardHandler;
            String clipboardText = entry.toString();
            keyboard.setClipboard(clipboardText);
            Boolean writeFile = this.fileSetting.getValue();
            if (writeFile) {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = timestampFormatter;
                String timestamp = now.format(formatter);
                entry.append(" | ").append(timestamp);
                this.appendRetainedEntry(entry.toString());
            }
        }
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        if (ClientAccess.evernightMinecraft.player != null) {
            Setting<Boolean> disconnectSettingSnapshot = this.disconnectSetting;
            Boolean saveOnDisconnect = disconnectSettingSnapshot.getValue();
            if (saveOnDisconnect) {
                this.saveCoordinates("Disconnect");
            }
        }
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        Object[] formatArguments = new Object[4];
        LocalPlayer xPlayer = ClientAccess.evernightMinecraft.player;
        int blockX = xPlayer.getBlockX();
        formatArguments[0] = blockX;
        LocalPlayer yPlayer = ClientAccess.evernightMinecraft.player;
        int blockY = yPlayer.getBlockY();
        formatArguments[1] = blockY;
        LocalPlayer zPlayer = ClientAccess.evernightMinecraft.player;
        int blockZ = zPlayer.getBlockZ();
        formatArguments[(2)] = blockZ;
        DimensionType dimension = WorldUtils.getDimension();
        formatArguments[(3)] = dimension.getEnglishDisplayName();
        this.coordinateText = ("X: %d, Y: %d, Z: %d in The %s").formatted(formatArguments);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
            Setting<Boolean> deathSettingSnapshot = this.deathSetting;
            Boolean saveOnDeath = deathSettingSnapshot.getValue();
            if (saveOnDeath) {
                ClientAccess.evernightMinecraft.execute(() -> this.saveCoordinates("Death"));
            }
        }
    }
}