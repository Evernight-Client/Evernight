package evernight.client.config.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
