package evernight.client.modules.render.VaultESP;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Local record of the ominous vaults the player already right-clicked.
 *
 * <p>Some servers never resend the block state after a vault was opened, so the client cannot tell a
 * fresh vault from a looted one. Right-clicking a vault records it here, keyed by server address plus
 * dimension, and the ESP then treats it as opened. The file is {@code evernight/vaultopened.json};
 * serialisation and file access are separate so the format stays readable.
 */
public final class VaultOpenedStore implements ClientAccess {
    private static final String FILE_NAME = "vaultopened.json";
    private static final int FORMAT_VERSION = 1;

    /** Scope key (server plus dimension) to recorded {@code "x,y,z"} positions. */
    private final Map<String, Set<String>> scopes = new LinkedHashMap<>();
    private boolean dirty;

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public boolean isOpened(String scope, int x, int y, int z) {
        if (scope == null) {
            return false;
        }
        Set<String> positions = this.scopes.get(scope);
        return positions != null && positions.contains(key(x, y, z));
    }

    /** Records one right-clicked vault; returns {@code true} when it was not recorded before. */
    public boolean mark(String scope, int x, int y, int z) {
        if (scope == null) {
            return false;
        }
        Set<String> positions = this.scopes.computeIfAbsent(scope, ignored -> new LinkedHashSet<>());
        boolean added = positions.add(key(x, y, z));
        if (added) {
            this.dirty = true;
        }
        return added;
    }

    public int count(String scope) {
        Set<String> positions = scope == null ? null : this.scopes.get(scope);
        return positions == null ? 0 : positions.size();
    }

    public int totalCount() {
        int total = 0;
        for (Set<String> positions : this.scopes.values()) {
            total += positions.size();
        }
        return total;
    }

    public void clearAll() {
        if (this.scopes.isEmpty()) {
            return;
        }
        this.scopes.clear();
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonObject scopeObjects = new JsonObject();
        for (Map.Entry<String, Set<String>> entry : this.scopes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            JsonArray positions = new JsonArray();
            for (String position : entry.getValue()) {
                positions.add(position);
            }
            scopeObjects.add(entry.getKey(), positions);
        }
        root.add("scopes", scopeObjects);
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
            JsonElement scopeElement = parsed.getAsJsonObject().get("scopes");
            if (scopeElement == null || !scopeElement.isJsonObject()) {
                return store;
            }
            for (Map.Entry<String, JsonElement> entry : scopeElement.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonArray()) {
                    continue;
                }
                Set<String> positions = new LinkedHashSet<>();
                for (JsonElement position : entry.getValue().getAsJsonArray()) {
                    if (position.isJsonPrimitive()) {
                        positions.add(position.getAsString());
                    }
                }
                if (!positions.isEmpty()) {
                    store.scopes.put(entry.getKey(), positions);
                }
            }
        } catch (RuntimeException malformedFile) {
            // A corrupt file behaves like an empty record: showing a looted vault again is harmless.
        }
        return store;
    }

    /** {@code <game directory>/evernight/vaultopened.json}. */
    public static Path path() {
        return FabricLoader.getInstance().getGameDir().resolve("evernight").resolve(FILE_NAME);
    }

    public static VaultOpenedStore load() {
        try {
            Path file = path();
            if (!Files.exists(file)) {
                return new VaultOpenedStore();
            }
            return fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadableFile) {
            return new VaultOpenedStore();
        }
    }

    /** Writes pending changes; a failure keeps the dirty flag so the next call retries. */
    public boolean saveIfDirty() {
        if (!this.dirty) {
            return true;
        }
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, this.toJson(), StandardCharsets.UTF_8);
            this.dirty = false;
            return true;
        } catch (IOException | RuntimeException unwritableFile) {
            return false;
        }
    }
}
