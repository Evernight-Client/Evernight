package evernight.client.utils.search;

import evernight.client.ClientAccess;
import evernight.client.EvernightClient;
import evernight.client.modules.client.UI.UIModule;
import me.towdium.pinin.PinIn;

/**
 * Lazily built {@link PinIn} context shared by every search box.
 *
 * <p>The dictionary is parsed once and only when Chinese search is actually used; the client
 * initialization warms it up on a worker thread so the first keystroke does not pay the parsing
 * cost. Matching is serialized on the context because the matcher keeps one scratch element for
 * characters that are not in the dictionary, and the chest search bar can be driven from module
 * logic as well as from rendering.
 *
 * <p>The fuzzy spellings exposed in {@code UI > 搜索} are the PinIn {@code Config} options; they are
 * applied under the same lock, so toggling a row takes effect on the next match without rebuilding
 * the dictionary.
 */
public final class PinyinMatcher {
    private static final int FUZZY_ZH2Z = 1;
    private static final int FUZZY_SH2S = 1 << 1;
    private static final int FUZZY_CH2C = 1 << 2;
    private static final int FUZZY_ANG2AN = 1 << 3;
    private static final int FUZZY_ENG2EN = 1 << 4;
    private static final int FUZZY_ING2IN = 1 << 5;
    private static final int FUZZY_U2V = 1 << 6;
    private static final int FUZZY_ALL = FUZZY_ZH2Z | FUZZY_SH2S | FUZZY_CH2C | FUZZY_ANG2AN
            | FUZZY_ENG2EN | FUZZY_ING2IN | FUZZY_U2V;

    private static volatile PinIn pinin;
    private static volatile boolean unavailable;
    private static int configuredMask = -1;

    private PinyinMatcher() {
    }

    public static boolean contains(String text, String query) {
        if (text == null || text.isEmpty() || query == null || query.isEmpty() || unavailable) {
            return false;
        }
        try {
            PinIn context = instance();
            int mask = currentFuzzyMask();
            synchronized (context) {
                if (configuredMask != mask) {
                    applyFuzzy(context, mask);
                    configuredMask = mask;
                }
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
                local = new PinIn();
                pinin = local;
            }
            return local;
        }
    }

    /**
     * Reads the fuzzy rows of {@code UI > 搜索}. The master {@code SearchFuzzy} switch gates every
     * individual spelling; before the UI module exists (or before a config is loaded) everything is on.
     * Also used by the search query cache to notice a settings change.
     */
    public static int currentFuzzyMask() {
        UIModule uiModule = UIModule.INSTANCE;
        if (uiModule == null) {
            return FUZZY_ALL;
        }
        if (!uiModule.searchFuzzySetting.getValue()) {
            return 0;
        }
        int mask = 0;
        if (uiModule.searchFuzzyZh2ZSetting.getValue()) mask |= FUZZY_ZH2Z;
        if (uiModule.searchFuzzySh2SSetting.getValue()) mask |= FUZZY_SH2S;
        if (uiModule.searchFuzzyCh2CSetting.getValue()) mask |= FUZZY_CH2C;
        if (uiModule.searchFuzzyAng2AnSetting.getValue()) mask |= FUZZY_ANG2AN;
        if (uiModule.searchFuzzyEng2EnSetting.getValue()) mask |= FUZZY_ENG2EN;
        if (uiModule.searchFuzzyIng2InSetting.getValue()) mask |= FUZZY_ING2IN;
        if (uiModule.searchFuzzyU2VSetting.getValue()) mask |= FUZZY_U2V;
        return mask;
    }

    private static void applyFuzzy(PinIn context, int mask) {
        context.config()
                .fZh2Z((mask & FUZZY_ZH2Z) != 0)
                .fSh2S((mask & FUZZY_SH2S) != 0)
                .fCh2C((mask & FUZZY_CH2C) != 0)
                .fAng2An((mask & FUZZY_ANG2AN) != 0)
                .fEng2En((mask & FUZZY_ENG2EN) != 0)
                .fIng2In((mask & FUZZY_ING2IN) != 0)
                .fU2V((mask & FUZZY_U2V) != 0)
                .commit();
    }
}
