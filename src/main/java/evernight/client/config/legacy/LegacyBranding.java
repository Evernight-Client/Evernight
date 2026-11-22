package evernight.client.config.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.EvernightClient;

import java.util.Locale;

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

    public static String canonicalModuleName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "antivoid" -> "Avoid";
            case "autoupgrade" -> "AutoTemplate";
            case "discordnotifs" -> "WebhookNotifs";
            default -> name;
        };
    }

    public static JsonElement findModuleConfig(JsonObject config, String moduleName) {
        if (config.has(moduleName)) {
            return config.get(moduleName);
        }
        return switch (moduleName) {
            case "Avoid" -> config.get("AntiVoid");
            case "AutoTemplate" -> config.get("AutoUpgrade");
            case "WebhookNotifs" -> config.get("DiscordNotifs");
            default -> null;
        };
    }

    public static JsonElement migrateModule(String moduleName, JsonElement input) {
        if (!(input instanceof JsonObject module) || !(module.get("settings") instanceof JsonObject)) {
            return input;
        }
        if (!"Watermark".equals(moduleName) && !"Welcomer".equals(moduleName)
                && !"AutoSign".equals(moduleName) && !"DiscordNotifs".equals(moduleName)
                && !"WebhookNotifs".equals(moduleName) && !"FeetPlace".equals(moduleName)
                && !"AutoFarm".equals(moduleName) && !"EntityControl".equals(moduleName)) {
            return input;
        }
        JsonObject migrated = module.deepCopy();
        JsonObject settings = migrated.getAsJsonObject("settings");
        switch (moduleName) {
            case "AutoFarm" -> {
                if (!settings.has("Frequency") && settings.has("BPT")) {
                    settings.add("Frequency", settings.get("BPT"));
                }
                settings.remove("BPT");
            }
            case "EntityControl" -> {
                if (!settings.has("Baritone") && settings.has("PathFinder")) {
                    settings.add("Baritone", settings.get("PathFinder"));
                }
                settings.remove("PathFinder");
            }
            case "FeetPlace" -> {
                JsonElement blockIgnore = settings.get("BlockIgnore");
                JsonElement mineIgnore = settings.get("MineIgnore");
                boolean mining = mineIgnore != null && mineIgnore.isJsonPrimitive()
                        && mineIgnore.getAsJsonPrimitive().isBoolean() && mineIgnore.getAsBoolean();
                if (blockIgnore != null && blockIgnore.isJsonPrimitive() && blockIgnore.getAsJsonPrimitive().isBoolean()) {
                    settings.addProperty("BlockIgnore", blockIgnore.getAsBoolean() ? "Both" : mining ? "Mining" : "None");
                } else if (blockIgnore == null && mining) {
                    settings.addProperty("BlockIgnore", "Mining");
                }
                settings.remove("MineIgnore");
                JsonElement sequential = settings.get("Sequential");
                if (sequential != null && sequential.isJsonPrimitive() && sequential.getAsJsonPrimitive().isBoolean()) {
                    settings.addProperty("Sequential", sequential.getAsBoolean() ? "Strict" : "None");
                }
            }
            case "Watermark" -> replaceDefault(settings, "Text", "Mio", EvernightClient.NAME);
            case "Welcomer" -> replaceDefault(settings, "Text", "Welcome to Mio.", "Welcome to Evernight.");
            case "AutoSign" -> {
                replaceDefault(settings, "Line1", "get mioclient", "get Evernight");
                if (equalsString(settings.get("Line3"), "www.mioclient.me")) {
                    if (equalsString(settings.get("Line4"), ":p")) {
                        settings.addProperty("Line3", "github.com/");
                        settings.addProperty("Line4", "spectrumdevs/evernight");
                    } else {
                        settings.addProperty("Line3", EvernightClient.PROJECT_URL);
                    }
                }
            }
            case "DiscordNotifs", "WebhookNotifs" -> {
                if (!settings.has("IgnoreEvernight") && settings.has("IgnoreMio")) {
                    settings.add("IgnoreEvernight", settings.get("IgnoreMio"));
                }
                settings.remove("IgnoreMio");
            }
            default -> throw new AssertionError(moduleName);
        }
        return migrated;
    }

    private static void replaceDefault(JsonObject settings, String key, String previous, String current) {
        if (equalsString(settings.get(key), previous)) {
            settings.addProperty(key, current);
        }
    }

    private static boolean equalsString(JsonElement value, String expected) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && expected.equals(value.getAsString());
    }
}