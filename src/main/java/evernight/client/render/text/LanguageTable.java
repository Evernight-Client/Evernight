package evernight.client.render.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.utils.inventory.CharacterConsumer;
import net.minecraft.ChatFormatting;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Flat translation table for the client UI.
 *
 * <p>The table is loaded from {@code assets/evernight/lang/<code>.json}. Addons can merge their own
 * files through {@link #registerOverlay(String, String, InputStream)}: overlays are kept per language
 * code and re-applied whenever the player switches languages, so addon text survives the reload that
 * {@link #load(InputStream)} performs.
 */
public final class LanguageTable {
    private static final String DEFAULT_LANGUAGE = "en_us";

    private final Map<String, String> translations = new HashMap<>();
    private final Map<String, Map<String, Map<String, String>>> overlays = new HashMap<>();
    private String activeLanguage = DEFAULT_LANGUAGE;

    public LanguageTable() {
        InputStream languageInput = UIModule.LanguageChoice.ENGLISH.openStream();
        this.load(languageInput);
    }

    private static Map<String, String> parse(String languageJson) {
        HashMap<String, String> table = new HashMap<>();
        JsonObject translations = JsonParser.parseString(languageJson).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : translations.entrySet()) {
            JsonElement element = entry.getValue();
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                continue;
            }
            String key = entry.getKey();
            String value = element.getAsString();
            value = value.replace("<red>", ChatFormatting.RED.toString());
            value = value.replace("<yellow>", ChatFormatting.YELLOW.toString());
            table.put(key, value);
        }
        return table;
    }

    /**
     * Replaces the base table and re-applies the overlays registered for {@link #getActiveLanguage()}.
     */
    public void load(InputStream input) {
        try {
            Map<String, String> parsed = parse(IOUtils.toString(input, StandardCharsets.UTF_8));
            this.translations.clear();
            this.translations.putAll(parsed);
            this.applyOverlays();
        } catch (Throwable ignoredFailure) {
        }
    }

    /**
     * Switches the active language and loads the base table for it. Addon overlays for that language are
     * merged on top.
     */
    public void load(InputStream input, String languageCode) {
        this.activeLanguage = languageCode;
        this.load(input);
    }

    public String getActiveLanguage() {
        return this.activeLanguage;
    }

    /**
     * Merges an addon language file into the table under a stable source id. Registering the same
     * source/language pair again replaces the previous entries.
     *
     * @param sourceId      unique owner id (the addon mod id), used to replace previous entries
     * @param languageCode  {@code en_us}, {@code zh_cn}, ... (must match the client's language codes)
     * @param input         UTF-8 JSON stream read but not closed by this method
     */
    public void registerOverlay(String sourceId, String languageCode, InputStream input) {
        String languageJson;
        try {
            languageJson = IOUtils.toString(input, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Could not read language overlay", failure);
        }
        Map<String, String> parsed = parse(languageJson);
        Map<String, Map<String, String>> languageOverlays =
                this.overlays.computeIfAbsent(languageCode, ignored -> new HashMap<>());
        languageOverlays.put(sourceId, parsed);
        if (languageCode.equals(this.activeLanguage)) {
            this.translations.putAll(parsed);
        }
    }

    private void applyOverlays() {
        Map<String, Map<String, String>> languageOverlays = this.overlays.get(this.activeLanguage);
        if (languageOverlays == null) {
            return;
        }
        for (Map<String, String> entries : languageOverlays.values()) {
            this.translations.putAll(entries);
        }
    }

    public void forEachCharacter(CharacterConsumer consumer) {
        for (String value : this.translations.values()) {
            for (int index = 0; index < value.length(); index++) {
                consumer.accept(value.charAt(index));
            }
        }
    }

    public String getTranslation(String key) {
        return this.translations.get(key);
    }

    public String getTranslationOrDefault(String key, String fallback) {
        return this.translations.getOrDefault(key, fallback);
    }

    public boolean hasTranslation(String key) {
        return this.translations.containsKey(key);
    }

    public int size() {
        return this.translations.size();
    }
}
