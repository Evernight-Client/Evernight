package evernight.client.utils.search;

import evernight.client.EvernightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks up display names in a fixed language, independent of the language the game is running in.
 *
 * <p>Search needs the English name of a Chinese client and the Chinese name of an English client, so
 * the vanilla language of a given code is parsed from the resource manager once and cached. Vanilla
 * keys ({@code item.minecraft.*}) and mod keys are both covered because {@link ClientLanguage} walks
 * every loaded namespace. Evernight's own keys additionally fall back to the language file inside the
 * mod jar, which is always present even if the resource pack order changes.
 */
public final class LocalizedNames {
    private static final String ENGLISH = "en_us";
    private static final String CHINESE = "zh_cn";
    private static final Map<String, Language> TABLES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> CLIENT_TABLES = new ConcurrentHashMap<>();

    private LocalizedNames() {
    }

    public static String english(String translationKey) {
        return lookup(ENGLISH, translationKey);
    }

    public static String chinese(String translationKey) {
        return lookup(CHINESE, translationKey);
    }

    public static String clientEnglish(String translationKey) {
        return clientLookup(ENGLISH, translationKey);
    }

    public static String clientChinese(String translationKey) {
        return clientLookup(CHINESE, translationKey);
    }

    private static String lookup(String languageCode, String translationKey) {
        Language table = table(languageCode);
        if (table == null) {
            return null;
        }
        return table.getOrDefault(translationKey, null);
    }

    private static Language table(String languageCode) {
        Language cached = TABLES.get(languageCode);
        if (cached != null) {
            return cached;
        }
        Language loaded = load(languageCode);
        if (loaded != null) {
            TABLES.put(languageCode, loaded);
        }
        return loaded;
    }

    private static Language load(String languageCode) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getResourceManager() == null) {
                return null;
            }
            return ClientLanguage.loadFrom(minecraft.getResourceManager(), List.of(languageCode), false);
        } catch (Throwable failure) {
            EvernightClient.LOGGER.warn("Could not load the {} language table for search", languageCode, failure);
            return null;
        }
    }

    private static String clientLookup(String languageCode, String translationKey) {
        return clientTable(languageCode).get(translationKey);
    }

    private static Map<String, String> clientTable(String languageCode) {
        return CLIENT_TABLES.computeIfAbsent(languageCode, LocalizedNames::loadClientTable);
    }

    private static Map<String, String> loadClientTable(String languageCode) {
        Map<String, String> entries = new HashMap<>();
        String resource = "/assets/evernight/lang/" + languageCode + ".json";
        try (InputStream stream = EvernightClient.class.getResourceAsStream(resource)) {
            if (stream != null) {
                Language.loadFromJson(stream, entries::put);
            }
        } catch (Throwable failure) {
            EvernightClient.LOGGER.warn("Could not read {} for search", resource, failure);
        }
        return Map.copyOf(entries);
    }
}
