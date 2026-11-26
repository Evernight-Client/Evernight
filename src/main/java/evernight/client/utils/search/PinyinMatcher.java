package evernight.client.utils.search;

import evernight.client.ClientAccess;
import evernight.client.EvernightClient;
import me.towdium.pinin.PinIn;

/**
 * Lazily built {@link PinIn} context shared by every search box.
 *
 * <p>The dictionary is parsed once and only when Chinese search is actually used; the client
 * initialization warms it up on a worker thread so the first keystroke does not pay the parsing
 * cost. Matching is serialized on the context because the matcher keeps one scratch element for
 * characters that are not in the dictionary, and the chest search bar can be driven from module
 * logic as well as from rendering.
 */
public final class PinyinMatcher {
    private static volatile PinIn pinin;
    private static volatile boolean unavailable;

    private PinyinMatcher() {
    }

    public static boolean contains(String text, String query) {
        if (text == null || text.isEmpty() || query == null || query.isEmpty() || unavailable) {
            return false;
        }
        try {
            PinIn context = instance();
            synchronized (context) {
                return context.contains(text, query);
            }
        } catch (Throwable failure) {
            unavailable = true;
            EvernightClient.LOGGER.warn("Chinese pinyin search is unavailable", failure);
            return false;
        }
    }

    /**
     * Parses the dictionary on a worker thread so the first Chinese search does not stall a frame.
     * Safe to call repeatedly; a failure leaves pinyin matching disabled instead of breaking search.
     */
    public static void preloadAsync() {
        if (pinin != null || unavailable) {
            return;
        }
        ClientAccess.executorService.submit(() -> {
            try {
                instance();
            } catch (Throwable failure) {
                unavailable = true;
                EvernightClient.LOGGER.warn("Could not preload the pinyin dictionary", failure);
            }
        });
    }

    private static PinIn instance() {
        PinIn local = pinin;
        if (local != null) {
            return local;
        }
        synchronized (PinyinMatcher.class) {
            local = pinin;
            if (local == null) {
                local = createContext();
                pinin = local;
            }
            return local;
        }
    }

    /**
     * Builds the shared context with the fuzzy spellings Chinese players expect from an IME:
     * {@code zh/z}, {@code sh/s}, {@code ch/c}, {@code ang/an}, {@code eng/en}, {@code ing/in} and
     * {@code v/u}. They are what lets {@code zongguo}, {@code san} or {@code nu} find 中国, 上 and 女.
     */
    private static PinIn createContext() {
        return new PinIn().config()
                .fZh2Z(true)
                .fSh2S(true)
                .fCh2C(true)
                .fAng2An(true)
                .fEng2En(true)
                .fIng2In(true)
                .fU2V(true)
                .commit();
    }
}
