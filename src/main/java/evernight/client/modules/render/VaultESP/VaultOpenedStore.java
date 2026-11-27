package evernight.client.modules.render.VaultESP;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Standalone record of the ominous vaults the player already right-clicked.
 *
 * <p>Some servers never resend the block state after a vault was opened, so the client cannot tell a
 * fresh vault from a looted one. Right-clicking a vault records it here; records are global and do
 * not distinguish servers or dimensions, and the ESP then treats the position as opened.
 *
 * <p>The records live in their own file, {@code <game directory>/evernight/vault-opened.json}, and are
 * loaded lazily the first time any code asks for the store, so they are independent of the ESP
 * module's enabled state. Every new record is written immediately; writes go through a temporary file
 * and a move so a crash cannot leave a truncated file behind.
 *
 * <p>Older files stored positions per server and dimension; those scopes are merged into the single
 * global set when the file is read.
 */
public final class VaultOpenedStore implements ClientAccess {
    private static final String FILE_NAME = "vault-opened.json";
    /** File written by the first implementation, read once and migrated on demand. */
    private static final String LEGACY_FILE_NAME = "vaultopened.json";
    private static final int FORMAT_VERSION = 2;

    private static VaultOpenedStore shared;

    /** Recorded {@code "x,y,z"} positions, shared across every server and dimension. */
    private final Set<String> positions = new LinkedHashSet<>();
    private boolean dirty;

    private VaultOpenedStore() {
    }

    /** The process-wide record, loaded from disk on first use. */
    public static synchronized VaultOpenedStore shared() {
        if (shared == null) {
            shared = load();
        }
        return shared;
    }

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public boolean isOpened(int x, int y, int z) {
        return this.positions.contains(key(x, y, z));
    }

    /** Records one right-clicked vault; returns {@code true} when it was not recorded before. */
    public boolean mark(int x, int y, int z) {
        boolean added = this.positions.add(key(x, y, z));
        if (added) {
            this.dirty = true;
        }
        return added;
    }

    public int count() {
        return this.positions.size();
    }

    public void clearAll() {
        if (this.positions.isEmpty()) {
            return;
        }
        this.positions.clear();
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonArray recorded = new JsonArray();
        for (String position : this.positions) {
            recorded.add(position);
        }
        root.add("positions", recorded);
        return gson.toJson(root);
    }

    public static VaultOpenedStore fromJson(String text) {
        VaultOpenedStore store = new VaultOpenedStore();
        if (text == null || text.isBlank()) {
            return store;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return store;
            }
            JsonObject root = parsed.getAsJsonObject();
            readPositions(root.get("positions"), store.positions);
            JsonElement scopes = root.get("scopes");
            if (scopes != null && scopes.isJsonObject()) {
                for (Map.Entry<String, JsonElement> scope : scopes.getAsJsonObject().entrySet()) {
                    readPositions(scope.getValue(), store.positions);
                }
            }
        } catch (RuntimeException malformedFile) {
            // A corrupt file behaves like an empty record: showing a looted vault again is harmless.
        }
        return store;
    }

    private static void readPositions(JsonElement element, Set<String> target) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        for (JsonElement position : element.getAsJsonArray()) {
            if (position.isJsonPrimitive()) {
                target.add(position.getAsString());
            }
        }
    }

    /** {@code <game directory>/evernight/vault-opened.json}. */
    public static Path path() {
        return FabricLoader.getInstance().getGameDir().resolve("evernight").resolve(FILE_NAME);
    }

    private static VaultOpenedStore load() {
        Path file = path();
        Path legacy = file.resolveSibling(LEGACY_FILE_NAME);
        Path source = Files.exists(file) ? file : (Files.exists(legacy) ? legacy : null);
        if (source == null) {
            return new VaultOpenedStore();
        }
        try {
            VaultOpenedStore store = fromJson(Files.readString(source, StandardCharsets.UTF_8));
            if (source.equals(legacy)) {
                store.dirty = true;
            }
            return store;
        } catch (IOException | RuntimeException unreadableFile) {
            return new VaultOpenedStore();
        }
    }

    /** Writes pending changes; a failure keeps the dirty flag so the next call retries. */
    public boolean saveIfDirty() {
        if (!this.dirty) {
            return true;
        }
        Path file = path();
        Path temporary = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, this.toJson(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException fallbackMove) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
            return true;
        } catch (IOException | RuntimeException unwritableFile) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignoredCleanupFailure) {
            }
            return false;
        }
    }
}
