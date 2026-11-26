package evernight.client.settings.options;

import evernight.client.ClientContext;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.render.text.LanguageTable;
import evernight.client.settings.core.NamedEntry;

/**
 * Which name sources the client search boxes match against.
 *
 * <p>{@link #AUTO} follows the active interface language, so a Chinese client searches with Chinese
 * names and smart pinyin while an English client searches English names and registry ids. The two
 * explicit values pin the behaviour regardless of the language switch.
 */
public enum SearchMode implements NamedEntry {
    AUTO("Auto"),
    CHINESE("Chinese"),
    ENGLISH("English");

    private final String displayName;

    SearchMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Resolves the mode configured in {@code UI > 搜索 > 搜索语言}, turning {@link #AUTO} into the
     * concrete mode of the active interface language. Falls back to English before the UI module
     * exists.
     */
    public static SearchMode effective() {
        UIModule uiModule = UIModule.INSTANCE;
        SearchMode selected = uiModule == null ? AUTO : uiModule.searchLanguageSetting.getValue();
        if (selected != AUTO) {
            return selected;
        }
        return usesChineseLanguage() ? CHINESE : ENGLISH;
    }

    public static boolean usesChineseLanguage() {
        LanguageTable table = ClientContext.languageTable;
        return table != null
                && UIModule.LanguageChoice.CHINESE.getLanguageCode().equals(table.getActiveLanguage());
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
