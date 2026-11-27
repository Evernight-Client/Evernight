package evernight.client.config.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.EvernightClient;

/**
 * 前身客户端（Mio）的品牌兼容：{@code mio-fabric} 数据目录与 {@code "client": "mio-fabric"} 预设。
 *
 * <p>这层是受支持的前身版本数据入口，长期保留。旧配置键/旧值换算在
 * {@link LegacyConfigMigrations}。
 */
public final class LegacyBranding {
    /**
     * Mod id used by the predecessor client ("Mio"). Presets exported by it start with
     * {@code "client": "mio-fabric"} and are still accepted by the preset importer.
     */
    public static final String LEGACY_CLIENT_ID = "mio-fabric";

    private LegacyBranding() {
    }

    /**
     * Whether the JSON object carries the predecessor client's branding, i.e. {@code "client": "mio-fabric"}.
     */
    public static boolean isLegacyPreset(JsonObject preset) {
        JsonElement client = preset.get("client");
        return client != null && client.isJsonPrimitive() && client.getAsJsonPrimitive().isString()
                && LEGACY_CLIENT_ID.equalsIgnoreCase(client.getAsString());
    }

    /**
     * Returns a copy of a legacy preset whose {@code client} field is rewritten to Evernight, so an
     * imported file is indistinguishable from a preset exported by this client. Other fields (including
     * {@code category}) are preserved verbatim.
     */
    public static JsonObject normalizePresetMetadata(JsonObject preset) {
        JsonObject normalized = preset.deepCopy();
        normalized.addProperty("client", EvernightClient.MOD_ID);
        return normalized;
    }
}
