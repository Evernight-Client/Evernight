package evernight.client.config.legacy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.EvernightClient;
import evernight.client.managers.config.ScreenshotCredentialStore;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.managers.social.WebhookManager;
import evernight.client.modules.hud.Indicator.IndicatorModule;
import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.modules.misc.ExtraScreenshot.ExtraScreenshotModule;
import evernight.client.modules.render.Crosshair.CrosshairModule;
import evernight.client.settings.options.NoSlowModeChoice;
import evernight.client.settings.options.NoSlowStrictInventoryChoice;
import evernight.client.utils.core.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The single place where legacy-config compatibility is implemented.
 *
 * <p>Conversion happens only while reading: legacy key names, legacy value formats and legacy file
 * locations are translated into the current format, and nothing extra is written back —
 * {@code ConfigurationManager#save()} rewrites the whole file in the current format, so legacy keys
 * disappear naturally on the next save.
 *
 * <p>When adding compatibility for another old format, change this class only; do not scatter
 * {@code fromJson} overrides across modules. Currently covered:
 * <ul>
 *   <li>Module config key renames: {@link #canonicalModuleName}, {@link #findModuleConfig}
 *       ({@code AntiVoid} → {@code Avoid}, etc.)</li>
 *   <li>Per-module setting migrations: {@link #migrateModule} (AutoFarm, EntityControl, FeetPlace,
 *       Watermark, Welcomer, AutoSign, WebhookNotifs, ESP, Indicator, NoSlow, Crosshair, Baritone,
 *       ExtraScreenshot)</li>
 *   <li>Other configs: {@link #migrateWebhookConfig}, {@link #presetModuleName},
 *       {@link #isLegacyColorJson}</li>
 *   <li>Data directories: {@link #migrateSocialsFile} ({@code friends.json} → {@code socials.json}),
 *       {@link #migrateDataDirectories} ({@code autoez/} → {@code killsay/})</li>
 * </ul>
 *
 * <p>Legacy data handled elsewhere: the {@code mio-fabric} data directory and the
 * {@code "client": "mio-fabric"} preset branding are covered by {@link LegacyBranding} (a supported
 * predecessor version, kept long-term); {@code VaultOpenedStore}'s {@code vaultopened.json} is a
 * standalone state file, not config.
 */
public final class LegacyConfigMigrations {
    /** Legacy ESP group color keys: {@code <Group>}{@code Outline}/{@code Fill} → {@code <Group>}Color. */
    private static final List<String> ESP_COLOR_GROUPS = List.of("Players", "Hostiles", "Animals", "Exp", "Items");
    /** Legacy ESP pearl color keys: bare {@code Fill}/{@code Outline} → {@code PearlsFill}/{@code PearlsOutline}. */
    private static final List<String> ESP_PEARL_COLOR_KEYS = List.of("Fill", "Outline");

    private LegacyConfigMigrations() {
    }

    // ------------------------------------------------------------------ module names

    /**
     * Maps a predecessor module name/alias to its current config name; returns the name unchanged
     * when it is already current.
     */
    public static String canonicalModuleName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "antivoid" -> "Avoid";
            case "autoupgrade" -> "AutoTemplate";
            case "discordnotifs" -> "WebhookNotifs";
            default -> name;
        };
    }

    /**
     * Reads a module's config: prefers the current key name and falls back to the legacy key
     * ({@code AntiVoid}/{@code AutoUpgrade}/{@code DiscordNotifs}) when it is missing.
     */
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

    // ------------------------------------------------------------------ module settings

    /**
     * Migrates one module's config JSON to the current format. Returns the same instance unchanged
     * (no copy) when there is nothing to migrate; returns a deep copy when a migration applies, so
     * the caller's input object is never modified.
     *
     * <p>Some migrations need a module instance or write across modules; see {@code migrateBaritone}
     * and {@code migrateScreenshotClientId}.
     */
    public static JsonElement migrateModule(String moduleName, JsonElement input) {
        if (!(input instanceof JsonObject module) || !(module.get("settings") instanceof JsonObject)) {
            return input;
        }
        JsonObject migrated = module.deepCopy();
        JsonObject settings = migrated.getAsJsonObject("settings");
        boolean changed = switch (moduleName) {
            case "AutoFarm" -> migrateRenamedKey(settings, "BPT", "Frequency");
            case "EntityControl" -> migrateRenamedKey(settings, "PathFinder", "Baritone");
            case "FeetPlace" -> migrateFeetPlace(settings);
            case "Watermark" -> replaceDefaultText(settings, "Text", "Mio", EvernightClient.NAME);
            case "Welcomer" -> replaceDefaultText(settings, "Text", "Welcome to Mio.", "Welcome to Evernight.");
            case "AutoSign" -> migrateAutoSign(settings);
            case "WebhookNotifs" -> migrateRenamedKey(settings, "IgnoreMio", "IgnoreEvernight");
            case "ESP" -> migrateEsp(settings);
            case "Indicator" -> migrateIndicator(settings);
            case "NoSlow" -> migrateNoSlow(settings);
            case "Crosshair" -> migrateCrosshair(settings);
            case "Baritone" -> migrateBaritone(settings);
            case "ExtraScreenshot" -> migrateScreenshotClientId(settings);
            default -> false;
        };
        return changed ? migrated : input;
    }

    /** Renames a key: when the new key is missing, inherit the legacy value, then drop the legacy key. */
    private static boolean migrateRenamedKey(JsonObject settings, String legacyKey, String currentKey) {
        if (!settings.has(legacyKey)) {
            return false;
        }
        if (!settings.has(currentKey)) {
            settings.add(currentKey, settings.get(legacyKey));
        }
        settings.remove(legacyKey);
        return true;
    }

    private static boolean replaceDefaultText(JsonObject settings, String key, String previous, String current) {
        if (!equalsString(settings.get(key), previous)) {
            return false;
        }
        settings.addProperty(key, current);
        return true;
    }

    private static boolean migrateAutoSign(JsonObject settings) {
        boolean changed = replaceDefaultText(settings, "Line1", "get mioclient", "get Evernight");
        if (!equalsString(settings.get("Line3"), "www.mioclient.me")) {
            return changed;
        }
        if (equalsString(settings.get("Line4"), ":p")) {
            settings.addProperty("Line3", "github.com/");
            settings.addProperty("Line4", "spectrumdevs/evernight");
        } else {
            settings.addProperty("Line3", EvernightClient.PROJECT_URL);
        }
        return true;
    }

    /**
     * Legacy FeetPlace expressed "ignore mining" and "sequential placement" as two booleans; both are
     * now enum strings.
     */
    private static boolean migrateFeetPlace(JsonObject settings) {
        boolean changed = false;
        JsonElement blockIgnore = settings.get("BlockIgnore");
        boolean mining = booleanValue(settings.get("MineIgnore"));
        if (isBoolean(blockIgnore)) {
            settings.addProperty("BlockIgnore",
                    blockIgnore.getAsBoolean() ? "Both" : mining ? "Mining" : "None");
            changed = true;
        } else if (blockIgnore == null && mining) {
            settings.addProperty("BlockIgnore", "Mining");
            changed = true;
        }
        if (settings.remove("MineIgnore") != null) {
            changed = true;
        }
        JsonElement sequential = settings.get("Sequential");
        if (isBoolean(sequential)) {
            settings.addProperty("Sequential", sequential.getAsBoolean() ? "Strict" : "None");
            changed = true;
        }
        return changed;
    }

    /** Legacy ESP used an {@code Items} toggle, a {@code Box} display mode and "group + Outline/Fill" color keys. */
    private static boolean migrateEsp(JsonObject settings) {
        boolean legacyDraw = !settings.has("DrawItems") && settings.has("Items");
        boolean legacyMode = settings.has("ItemsMode") && "Box".equalsIgnoreCase(stringValue(settings.get("ItemsMode")));
        boolean legacyColors = ESP_COLOR_GROUPS.stream()
                .anyMatch(group -> settings.has(group + "Outline") || settings.has(group + "Fill"));
        boolean legacyPearls = ESP_PEARL_COLOR_KEYS.stream()
                .anyMatch(key -> isColorLike(settings.get(key)));
        if (!legacyDraw && !legacyMode && !legacyColors && !legacyPearls) {
            return false;
        }
        if (legacyDraw) {
            settings.add("DrawItems", settings.get("Items").deepCopy());
        }
        if (legacyMode) {
            settings.addProperty("ItemsMode", "Plain");
        }
        for (String group : ESP_COLOR_GROUPS) {
            if (!settings.has(group + "Color")) {
                JsonElement legacyColor = settings.has(group + "Outline")
                        ? settings.get(group + "Outline")
                        : settings.get(group + "Fill");
                if (legacyColor != null) {
                    settings.add(group + "Color", legacyColor.deepCopy());
                }
            }
            settings.remove(group + "Outline");
            settings.remove(group + "Fill");
        }
        for (String key : ESP_PEARL_COLOR_KEYS) {
            JsonElement legacyColor = settings.get(key);
            if (!isColorLike(legacyColor)) {
                continue;
            }
            if (!settings.has("Pearls" + key)) {
                settings.add("Pearls" + key, legacyColor.deepCopy());
            }
            settings.remove(key);
        }
        return true;
    }

    /**
     * Legacy Indicator used a single {@code Display} value or an {@code Indicators} array; it is now
     * one boolean per indicator.
     */
    private static boolean migrateIndicator(JsonObject settings) {
        boolean legacyArray = settings.get("Indicators") instanceof JsonArray;
        if (!legacyArray && !settings.has("Display")) {
            return false;
        }
        JsonArray selected = legacyArray ? settings.getAsJsonArray("Indicators") : new JsonArray();
        if (!legacyArray) {
            selected.add(settings.get("Display"));
        }
        settings.remove("Indicators");
        settings.remove("Display");
        for (IndicatorModule.DisplayMode mode : IndicatorModule.DisplayMode.values()) {
            settings.addProperty(mode.getName(), false);
        }
        for (JsonElement entry : selected) {
            String name = stringValue(entry);
            if (name.isEmpty()) {
                continue;
            }
            for (IndicatorModule.DisplayMode mode : IndicatorModule.DisplayMode.values()) {
                if (mode.getName().equalsIgnoreCase(name)) {
                    settings.addProperty(mode.getName(), true);
                }
            }
        }
        return true;
    }

    /** Legacy NoSlow's {@code GrimStrict} item mode and {@code Constantiam} inventory mode are both retired. */
    private static boolean migrateNoSlow(JsonObject settings) {
        boolean changed = false;
        if (equalsString(settings.get("Mode"), "GrimStrict")) {
            settings.addProperty("Mode", NoSlowModeChoice.GRIMV3.getName());
            changed = true;
        }
        if (equalsString(settings.get("StrictInventory"), "Constantiam")) {
            settings.addProperty("StrictInventory", NoSlowStrictInventoryChoice.NCP.getName());
            changed = true;
        }
        return changed;
    }

    /** Legacy Crosshair had only a "use vanilla crosshair" boolean; it is now a {@code Mode} enum. */
    private static boolean migrateCrosshair(JsonObject settings) {
        if (settings.has("Mode") || !isBoolean(settings.get("Vanilla"))) {
            return false;
        }
        boolean vanilla = settings.get("Vanilla").getAsBoolean();
        settings.addProperty("Mode", vanilla
                ? CrosshairModule.CrosshairMode.VANILLA.getName()
                : CrosshairModule.CrosshairMode.CUSTOM.getName());
        settings.remove("Vanilla");
        return true;
    }

    /**
     * Legacy Baritone stored {@code CensorCoords} itself; it now belongs to CoordinateHider.
     *
     * <p>Cross-module write: the target module's "do not overwrite an existing new value" check lives
     * in {@link CoordinateHiderModule#applyLegacyBaritoneCensor(boolean)}.
     */
    private static boolean migrateBaritone(JsonObject settings) {
        if (!isBoolean(settings.get("CensorCoords"))) {
            return false;
        }
        CoordinateHiderModule.applyLegacyBaritoneCensor(settings.get("CensorCoords").getAsBoolean());
        settings.remove("CensorCoords");
        return true;
    }

    /**
     * Older versions stored {@code ClientId} in {@code modules.json}; it is now stored only in
     * {@code screenshot.json}.
     *
     * <p>Migrates only during a normal config load — importing a preset must not extract someone
     * else's credential. See {@link ClientContext#configurationLoading}.
     */
    private static boolean migrateScreenshotClientId(JsonObject settings) {
        JsonElement legacy = settings.get("ClientId");
        if (legacy == null || legacy.isJsonNull() || !legacy.isJsonPrimitive()) {
            return false;
        }
        ScreenshotCredentialStore store = ClientContext.screenshotCredentialStore;
        if (store == null || store.isLoadedFromFile() || !ClientContext.configurationLoading) {
            return false;
        }
        String clientId = legacy.getAsString();
        store.setClientId(clientId);
        ExtraScreenshotModule module = ClientContext.getModule(ExtraScreenshotModule.class);
        if (module != null) {
            module.clientIdSetting.setValue(clientId);
        }
        settings.remove("ClientId");
        return true;
    }

    // ------------------------------------------------------------------ other configs

    /**
     * Legacy {@code webhook.json} stuffed the whole Telegram bot URL into {@code url} and stored the
     * chat id separately in {@code telegramChatId}; returns a normalized copy so
     * {@code WebhookManager#fromJson} can read the current keys directly.
     */
    public static JsonObject migrateWebhookConfig(JsonObject config) {
        JsonObject migrated = config.deepCopy();
        String storedUrl = stringValue(migrated.get("url"));
        String botToken = "";
        String chatId = "";
        if (migrated.get("telegram") instanceof JsonObject telegram) {
            botToken = stringValue(telegram.get("botToken"));
            chatId = stringValue(telegram.get("chatId"));
        }
        String legacyChatId = stringValue(migrated.get("telegramChatId"));
        if (chatId.isEmpty() && !legacyChatId.isEmpty()) {
            chatId = legacyChatId;
        }
        migrated.remove("telegramChatId");
        String url = storedUrl;
        if (!url.isEmpty()) {
            try {
                WebhookManager.TelegramEndpoint legacyEndpoint = WebhookManager.parseTelegramUrl(url);
                if (legacyEndpoint != null) {
                    if (botToken.isEmpty()) {
                        botToken = legacyEndpoint.botToken();
                    }
                    if (chatId.isEmpty()) {
                        chatId = legacyEndpoint.chatId();
                    }
                    url = "";
                }
            } catch (IllegalArgumentException invalidLegacyUrl) {
                url = "";
            }
        }
        migrated.addProperty("url", url);
        JsonObject telegram = new JsonObject();
        telegram.addProperty("botToken", botToken);
        telegram.addProperty("chatId", chatId);
        migrated.add("telegram", telegram);
        return migrated;
    }

    /**
     * Module name of a single-module clipboard preset: the current field is {@code m}, older versions
     * used {@code module}.
     *
     * @throws IllegalArgumentException when neither field is present
     */
    public static String presetModuleName(JsonObject preset) {
        if (preset.has("m")) {
            return preset.get("m").getAsString();
        }
        if (preset.has("module")) {
            return preset.get("module").getAsString();
        }
        throw new IllegalArgumentException();
    }

    /**
     * Older versions stored colors as a bare {@code [r,g,b,a]} array; the current format is
     * {@code {"rgba":[...],...}}.
     */
    public static boolean isLegacyColorJson(JsonElement json) {
        return json != null && json.isJsonArray();
    }

    // ------------------------------------------------------------------ data directories

    /**
     * Migrates legacy {@code friends.json} to {@code socials.json}; does nothing when the target
     * already exists or the source is missing.
     */
    public static void migrateSocialsFile(Path rootDirectory) throws IOException {
        Path friendsPath = rootDirectory.resolve("friends.json");
        Path socialsPath = rootDirectory.resolve("socials.json");
        if (!Files.exists(friendsPath) || Files.exists(socialsPath)) {
            return;
        }
        SocialRegistry socialRegistry = ClientContext.socialRegistry;
        if (socialRegistry == null) {
            return;
        }
        JsonObject friendsRoot = JsonParser.parseString(FileUtils.readUtf8(friendsPath)).getAsJsonObject();
        if (!(friendsRoot.get("friends") instanceof JsonArray friends)) {
            return;
        }
        Iterator<JsonElement> friendIterator = friends.iterator();
        while (friendIterator.hasNext()) {
            String name = friendIterator.next().getAsString();
            socialRegistry.addFriend(name);
        }
        FileUtils.writeUtf8(socialsPath, ClientAccess.gson.toJson(socialRegistry.toJson()));
        friendsPath.toFile().delete();
    }

    /** The legacy kill-message directory was named {@code autoez/}; it is now {@code killsay/}. */
    public static void migrateDataDirectories(Path rootDirectory, Path killsayDirectory) {
        try {
            File legacyAutoEz = rootDirectory.resolve("autoez").toFile();
            if (legacyAutoEz.isDirectory()) {
                legacyAutoEz.renameTo(killsayDirectory.toFile());
            }
        } catch (Exception ignoredMigrationFailure) {
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isBoolean(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
    }

    private static boolean booleanValue(JsonElement element) {
        return isBoolean(element) && element.getAsBoolean();
    }

    private static boolean isColorLike(JsonElement element) {
        return element != null && (element.isJsonArray() || element.isJsonObject());
    }

    private static boolean equalsString(JsonElement value, String expected) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && expected.equals(value.getAsString());
    }

    /** Value of a string primitive (trimmed); returns an empty string for anything else. */
    private static String stringValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return "";
        }
        return element.getAsString().trim();
    }
}
