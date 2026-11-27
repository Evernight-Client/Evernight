package evernight.client.config.legacy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBrandingTest {
    @Test
    @DisplayName("识别 mio-fabric 旧 preset，大小写不敏感")
    void identifiesLegacyPresets() {
        assertTrue(LegacyBranding.isLegacyPreset(object("{\"client\":\"mio-fabric\"}")));
        assertTrue(LegacyBranding.isLegacyPreset(object("{\"client\":\"MIO-FABRIC\"}")));
        assertFalse(LegacyBranding.isLegacyPreset(object("{\"client\":\"evernight\"}")));
        assertFalse(LegacyBranding.isLegacyPreset(object("{}")));
        assertFalse(LegacyBranding.isLegacyPreset(object("{\"client\":1}")));
    }

    @Test
    @DisplayName("规范化只改 client，深拷贝且保留其它字段")
    void normalizesMetadataWithoutMutatingInput() {
        JsonObject preset = object("{\"client\":\"mio-fabric\",\"name\":\"p\",\"category\":\"all\"}");
        JsonObject normalized = LegacyBranding.normalizePresetMetadata(preset);
        assertEquals("evernight", normalized.get("client").getAsString());
        assertEquals("p", normalized.get("name").getAsString());
        assertEquals("all", normalized.get("category").getAsString());
        assertEquals("mio-fabric", preset.get("client").getAsString());
        assertNotSame(preset, normalized);
    }

    @Test
    @DisplayName("旧模块名映射到新配置名")
    void canonicalModuleNames() {
        assertEquals("Avoid", LegacyBranding.canonicalModuleName("antivoid"));
        assertEquals("AutoTemplate", LegacyBranding.canonicalModuleName("AUTOUPGRADE"));
        assertEquals("WebhookNotifs", LegacyBranding.canonicalModuleName("DiscordNotifs"));
        assertEquals("Aura", LegacyBranding.canonicalModuleName("Aura"));
    }

    @Test
    @DisplayName("查找模块配置时优先新键名，再回落旧键名")
    void findsModuleConfigByNewOrLegacyName() {
        JsonObject config = object("{\"Avoid\":{\"new\":1},\"AntiVoid\":{\"old\":1},"
                + "\"AutoUpgrade\":{\"y\":2},\"DiscordNotifs\":{\"z\":3}}");
        assertEquals(1, LegacyBranding.findModuleConfig(config, "Avoid")
                .getAsJsonObject().get("new").getAsInt());
        assertEquals(2, LegacyBranding.findModuleConfig(config, "AutoTemplate")
                .getAsJsonObject().get("y").getAsInt());
        assertEquals(3, LegacyBranding.findModuleConfig(config, "WebhookNotifs")
                .getAsJsonObject().get("z").getAsInt());
        assertNull(LegacyBranding.findModuleConfig(config, "Unknown"));
    }

    @Test
    @DisplayName("非对象/无 settings 原样返回")
    void nonObjectInputsAreUntouched() {
        JsonElement primitive = new JsonPrimitive("x");
        assertSame(primitive, LegacyBranding.migrateModule("Watermark", primitive));
        JsonObject noSettings = object("{\"enabled\":true}");
        assertSame(noSettings, LegacyBranding.migrateModule("Watermark", noSettings));
        JsonObject settingsNotObject = object("{\"settings\":\"x\"}");
        assertSame(settingsNotObject, LegacyBranding.migrateModule("Watermark", settingsNotObject));
    }

    @Test
    @DisplayName("Watermark/Welcomer 只替换默认文案")
    void migratesDefaultTexts() {
        JsonObject watermark = object("{\"settings\":{\"Text\":\"Mio\"}}");
        assertEquals("Evernight", migrate(watermark, "Watermark")
                .getAsJsonObject("settings").get("Text").getAsString());
        assertEquals("Mio", watermark.getAsJsonObject("settings").get("Text").getAsString());

        JsonObject custom = object("{\"settings\":{\"Text\":\"My Client\"}}");
        assertEquals("My Client", migrate(custom, "Watermark")
                .getAsJsonObject("settings").get("Text").getAsString());

        JsonObject welcomer = object("{\"settings\":{\"Text\":\"Welcome to Mio.\"}}");
        assertEquals("Welcome to Evernight.", migrate(welcomer, "Welcomer")
                .getAsJsonObject("settings").get("Text").getAsString());
    }

    @Test
    @DisplayName("AutoSign 默认行与 mioclient 链接迁移")
    void migratesAutoSign() {
        JsonObject sign = object("{\"settings\":{\"Line1\":\"get mioclient\","
                + "\"Line3\":\"www.mioclient.me\",\"Line4\":\":p\"}}");
        JsonObject migrated = migrate(sign, "AutoSign").getAsJsonObject("settings");
        assertEquals("get Evernight", migrated.get("Line1").getAsString());
        assertEquals("github.com/", migrated.get("Line3").getAsString());
        assertEquals("spectrumdevs/evernight", migrated.get("Line4").getAsString());

        JsonObject otherLine4 = object("{\"settings\":{\"Line3\":\"www.mioclient.me\","
                + "\"Line4\":\"other\"}}");
        JsonObject other = migrate(otherLine4, "AutoSign").getAsJsonObject("settings");
        assertEquals("https://github.com/Evernight-Client/Evernight",
                other.get("Line3").getAsString());
        assertEquals("other", other.get("Line4").getAsString());
    }

    @Test
    @DisplayName("AutoFarm BPT 改名 Frequency，已有 Frequency 时保留")
    void migratesAutoFarm() {
        JsonObject onlyOld = object("{\"settings\":{\"BPT\":3}}");
        JsonObject migrated = migrate(onlyOld, "AutoFarm").getAsJsonObject("settings");
        assertEquals(3, migrated.get("Frequency").getAsInt());
        assertFalse(migrated.has("BPT"));

        JsonObject both = object("{\"settings\":{\"BPT\":3,\"Frequency\":2}}");
        JsonObject kept = migrate(both, "AutoFarm").getAsJsonObject("settings");
        assertEquals(2, kept.get("Frequency").getAsInt());
        assertFalse(kept.has("BPT"));
    }

    @Test
    @DisplayName("EntityControl PathFinder 改成 Baritone")
    void migratesEntityControl() {
        JsonObject input = object("{\"settings\":{\"PathFinder\":true}}");
        JsonObject migrated = migrate(input, "EntityControl").getAsJsonObject("settings");
        assertTrue(migrated.get("Baritone").getAsBoolean());
        assertFalse(migrated.has("PathFinder"));
    }

    @Test
    @DisplayName("FeetPlace 的 BlockIgnore/Sequential 布尔迁移")
    void migratesFeetPlace() {
        assertEquals("Both", feetPlace("{\"BlockIgnore\":true,\"MineIgnore\":false}")
                .get("BlockIgnore").getAsString());
        assertEquals("Mining", feetPlace("{\"BlockIgnore\":false,\"MineIgnore\":true}")
                .get("BlockIgnore").getAsString());
        assertEquals("None", feetPlace("{\"BlockIgnore\":false,\"MineIgnore\":false}")
                .get("BlockIgnore").getAsString());
        assertEquals("Mining", feetPlace("{\"MineIgnore\":true}")
                .get("BlockIgnore").getAsString());
        assertFalse(feetPlace("{\"BlockIgnore\":true,\"MineIgnore\":false}").has("MineIgnore"));

        assertEquals("Strict", feetPlace("{\"Sequential\":true}").get("Sequential").getAsString());
        assertEquals("None", feetPlace("{\"Sequential\":false}").get("Sequential").getAsString());
    }

    @Test
    @DisplayName("WebhookNotifs 的 IgnoreMio 改名 IgnoreEvernight")
    void migratesWebhookIgnoreFlag() {
        JsonObject migrated = migrate(object("{\"settings\":{\"IgnoreMio\":true}}"), "WebhookNotifs")
                .getAsJsonObject("settings");
        assertTrue(migrated.get("IgnoreEvernight").getAsBoolean());
        assertFalse(migrated.has("IgnoreMio"));
    }

    @Test
    @DisplayName("无关模块不迁移，保留原对象")
    void unrelatedModuleIsUntouched() {
        JsonObject input = object("{\"settings\":{\"Value\":1}}");
        assertSame(input, LegacyBranding.migrateModule("Aura", input));
    }

    private static JsonObject feetPlace(String settingsJson) {
        JsonObject migrated = migrate(object("{\"settings\":" + settingsJson + "}"), "FeetPlace");
        return migrated.getAsJsonObject("settings");
    }

    private static JsonObject migrate(JsonObject input, String moduleName) {
        JsonElement output = LegacyBranding.migrateModule(moduleName, input);
        return output.getAsJsonObject();
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
