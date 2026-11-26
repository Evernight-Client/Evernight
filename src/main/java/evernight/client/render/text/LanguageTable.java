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
 *
 * <p>Lookups follow one fallback chain: the active language first, then English, then
 * {@code null} (the caller decides what an entirely missing key should display). English base entries
 * and English overlays are always kept in memory, so a partially translated addon or a language file
 * with a missing key still shows readable English instead of a raw key. Callers that want a visible
 * placeholder should fall back to the key itself; see {@link Translations#get(String)}.
 */
public final class LanguageTable {
    private static final String DEFAULT_LANGUAGE = "en_us";

    /** Base English entries, kept even while another language is active. */
    private final Map<String, String> englishBase = new HashMap<>();
    /** Base entries of the currently active language. */
    private final Map<String, String> activeBase = new HashMap<>();
    /** sourceId -> languageCode -> entries */
    private final Map<String, Map<String, Map<String, String>>> overlays = new HashMap<>();
    /** Resolved view: English fallback entries first, active-language entries override them. */
    private final Map<String, String> translations = new HashMap<>();
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
     * Loads the base table for {@link #getActiveLanguage()} and rebuilds the resolved view. An English
     * load also refreshes the fallback table.
     */
    public void load(InputStream input) {
        try {
            Map<String, String> parsed = parse(IOUtils.toString(input, StandardCharsets.UTF_8));
            if (DEFAULT_LANGUAGE.equals(this.activeLanguage)) {
                this.englishBase.clear();
                this.englishBase.putAll(parsed);
            }
            this.activeBase.clear();
            this.activeBase.putAll(parsed);
            this.rebuild();
        } catch (Throwable ignoredFailure) {
        }
    }

    /**
     * Switches the active language and loads the base table for it. English keeps serving as the
     * fallback for keys the active language does not define, and addon overlays for both languages are
     * merged on top.
     */
    public void load(InputStream input, String languageCode) {
        this.activeLanguage = languageCode == null || languageCode.isBlank()
                ? DEFAULT_LANGUAGE : languageCode;
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
        this.rebuild();
    }

    /**
     * Rebuilds the resolved view as: English base, English overlays, active base, active overlays.
     * Active-language values therefore override English, and every English-only key remains readable.
     * When English is active the English pass and the active pass describe the same data.
     */
    private void rebuild() {
        this.translations.clear();
        this.translations.putAll(this.englishBase);
        this.applyOverlays(DEFAULT_LANGUAGE);
        if (!DEFAULT_LANGUAGE.equals(this.activeLanguage)) {
            this.translations.putAll(this.activeBase);
            this.applyOverlays(this.activeLanguage);
        }
    }

    private void applyOverlays(String languageCode) {
        Map<String, Map<String, String>> languageOverlays = this.overlays.get(languageCode);
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

    /**
     * Looks up a key with the active-language-then-English fallback chain.
     *
     * @return the translated text, or {@code null} when neither the active language nor English
     *         defines the key
     */
    public String getTranslation(String key) {
        return this.translations.get(key);
    }

    public String getTranslationOrDefault(String key, String fallback) {
        return this.translations.getOrDefault(key, fallback);
    }

    /**
     * Looks up one key in a fixed language, so callers can read the other language's text (and every
     * addon overlay registered for it) instead of only the resolved active-language view. English and
     * the active language also consult their base tables; other language codes only have overlays.
     *
     * @return the text, or {@code null} when neither a base table nor an overlay defines the key
     */
    public String getTranslationFor(String key, String languageCode) {
        if (key == null || languageCode == null) {
            return null;
        }
        String value = null;
        if (DEFAULT_LANGUAGE.equals(languageCode)) {
            value = this.englishBase.get(key);
        } else if (languageCode.equals(this.activeLanguage)) {
            value = this.activeBase.get(key);
        }
        Map<String, Map<String, String>> languageOverlays = this.overlays.get(languageCode);
        if (languageOverlays != null) {
            for (Map<String, String> entries : languageOverlays.values()) {
                String overlayValue = entries.get(key);
                if (overlayValue != null) {
                    value = overlayValue;
                }
            }
        }
        return value;
    }

    public boolean hasTranslation(String key) {
        return this.translations.containsKey(key);
    }

    public int size() {
        return this.translations.size();
    }
}
