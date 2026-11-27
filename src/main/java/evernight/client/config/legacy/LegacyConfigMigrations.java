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
 * 所有「旧配置」兼容的唯一实现处。
 *
 * <p>这里只做读取时的一次性换算：把旧键名、旧值格式和旧文件位置折算成当前格式，迁移结果不额外落盘——
 * {@code ConfigurationManager#save()} 每次都用当前格式整份重写，所以旧键在下一次保存时自然消失。
 *
 * <p>新增旧格式兼容时只改本类，不要在各模块里散写 {@code fromJson} 覆盖。目前覆盖：
 * <ul>
 *   <li>模块配置键改名：{@link #canonicalModuleName}、{@link #findModuleConfig}（{@code AntiVoid} →
 *       {@code Avoid} 等）</li>
 *   <li>单模块设置迁移：{@link #migrateModule}（AutoFarm、EntityControl、FeetPlace、Watermark、
 *       Welcomer、AutoSign、WebhookNotifs、ESP、Indicator、NoSlow、Crosshair、Baritone、ExtraScreenshot）</li>
 *   <li>其它配置：{@link #migrateWebhookConfig}、{@link #presetModuleName}、{@link #isLegacyColorJson}</li>
 *   <li>数据目录：{@link #migrateSocialsFile}（{@code friends.json} → {@code socials.json}）、
 *       {@link #migrateDataDirectories}（{@code autoez/} → {@code killsay/}）</li>
 * </ul>
 *
 * <p>不在这里的旧数据：{@code mio-fabric} 数据目录与 {@code "client": "mio-fabric"} 预设品牌由
 * {@link LegacyBranding} 处理（受支持的前身版本，长期保留）；{@code VaultOpenedStore} 的
 * {@code vaultopened.json} 是独立状态文件，不属于配置。
 */
public final class LegacyConfigMigrations {
    /** 旧的 ESP 分组颜色键：{@code <Group>}{@code Outline}/{@code Fill} → {@code <Group>}Color。 */
    private static final List<String> ESP_COLOR_GROUPS = List.of("Players", "Hostiles", "Animals", "Exp", "Items");
    /** 旧 ESP 的珍珠颜色键：裸 {@code Fill}/{@code Outline} → {@code PearlsFill}/{@code PearlsOutline}。 */
    private static final List<String> ESP_PEARL_COLOR_KEYS = List.of("Fill", "Outline");

    private LegacyConfigMigrations() {
    }

    // ------------------------------------------------------------------ 模块名

    /**
     * 前身版本的模块名/别名映射到当前配置名；已经是当前名时原样返回。
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
     * 读取模块配置：优先当前键名，缺失时回落旧键名（{@code AntiVoid}/{@code AutoUpgrade}/{@code DiscordNotifs}）。
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

    // ------------------------------------------------------------------ 模块设置

    /**
     * 把一个模块的配置 JSON 迁移成当前格式。没有可迁移内容时原样返回同一实例（不产生副本），
     * 需要迁移时返回深拷贝，调用方的输入对象不会被修改。
     *
     * <p>个别迁移需要模块实例或跨模块写入，见 {@code migrateBaritone}、{@code migrateScreenshotClientId}。
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

    /** 旧键换新键：新键缺失时继承旧值，随后删掉旧键。 */
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

    /** 旧 FeetPlace 用两个布尔表达「挖掘忽略」和「顺序放置」，现在分别是枚举字符串。 */
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

    /** 旧 ESP 用 {@code Items} 开关、{@code Box} 显示模式和「分组+Outline/Fill」颜色键。 */
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

    /** 旧 Indicator 用 {@code Display} 单值或 {@code Indicators} 数组，现在是每个指示器一个布尔。 */
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

    /** 旧 NoSlow 的 {@code GrimStrict} 物品模式和 {@code Constantiam} 背包模式都已退役。 */
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

    /** 旧 Crosshair 只有一个「使用原版准星」布尔，现在是 {@code Mode} 枚举。 */
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
     * 旧 Baritone 模块自己存 {@code CensorCoords}，现在归 CoordinateHider。
     *
     * <p>跨模块写入：目标模块的「已有新值不覆盖」判断在
     * {@link CoordinateHiderModule#applyLegacyBaritoneCensor(boolean)} 里。
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
     * 旧版本的 {@code ClientId} 存在 {@code modules.json}，现在只存 {@code screenshot.json}。
     *
     * <p>只在正常配置加载时迁移——预设导入不能把别人的凭据抽出来。见 {@link ClientContext#configurationLoading}。
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

    // ------------------------------------------------------------------ 其它配置

    /**
     * 旧 {@code webhook.json} 把 Telegram 机器人地址整个塞在 {@code url} 里，还把 chat id 单独存在
     * {@code telegramChatId}；返回规范化后的副本，供 {@code WebhookManager#fromJson} 直接读取当前键。
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
     * 单模块剪贴板预设的模块名：当前字段是 {@code m}，旧版本用 {@code module}。
     *
     * @throws IllegalArgumentException 两个字段都不存在
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
     * 旧版本把颜色存成裸 {@code [r,g,b,a]} 数组；当前格式是 {@code {"rgba":[...],...}}。
     */
    public static boolean isLegacyColorJson(JsonElement json) {
        return json != null && json.isJsonArray();
    }

    // ------------------------------------------------------------------ 数据目录

    /** 旧 {@code friends.json} 迁移成 {@code socials.json}；目标已存在或来源缺失时什么都不做。 */
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

    /** 旧的击杀喊话目录叫 {@code autoez/}，现在叫 {@code killsay/}。 */
    public static void migrateDataDirectories(Path rootDirectory, Path killsayDirectory) {
        try {
            File legacyAutoEz = rootDirectory.resolve("autoez").toFile();
            if (legacyAutoEz.isDirectory()) {
                legacyAutoEz.renameTo(killsayDirectory.toFile());
            }
        } catch (Exception ignoredMigrationFailure) {
        }
    }

    // ------------------------------------------------------------------ 工具

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

    /** 字符串原语的值（已 trim）；不是字符串原语时返回空串。 */
    private static String stringValue(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return "";
        }
        return element.getAsString().trim();
    }
}
