package evernight.client.modules;

import evernight.client.render.text.Translations;
import evernight.client.settings.core.NamedEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A module category, rendered as one ClickGUI panel.
 *
 * <p>The built-in categories are the stable constants {@link #COMBAT} through {@link #HUD}.
 * Addons register additional categories through
 * {@link evernight.addon.AddonContext#registerCategory(String)}. A category is an identity object:
 * {@link Module} keeps the exact instance it was constructed with, so registering another category
 * never changes how existing modules are grouped. Categories are never serialised to the config or
 * to presets.
 *
 * <p>{@link #values()} lists the built-in categories first, then addon categories in registration
 * order. {@link #ordinal()} is the index in that order and only drives ClickGUI panel sorting.
 */
public final class ModuleCategory implements NamedEntry {
    private static int nextOrdinal;

    public static final ModuleCategory COMBAT = new ModuleCategory("Combat");
    public static final ModuleCategory MISC = new ModuleCategory("Misc");
    public static final ModuleCategory RENDER = new ModuleCategory("Render");
    public static final ModuleCategory MOVEMENT = new ModuleCategory("Movement");
    public static final ModuleCategory PLAYER = new ModuleCategory("Player");
    public static final ModuleCategory EXPLOIT = new ModuleCategory("Exploit");
    public static final ModuleCategory CLIENT = new ModuleCategory("Client");
    public static final ModuleCategory HUD = new ModuleCategory("HUD");

    private static final List<ModuleCategory> BUILT_IN =
            List.of(COMBAT, MISC, RENDER, MOVEMENT, PLAYER, EXPLOIT, CLIENT, HUD);
    private static final List<ModuleCategory> ADDON_CATEGORIES = new CopyOnWriteArrayList<>();

    private final String name;
    private final String translationKey;
    private final int ordinal;

    private ModuleCategory(String name) {
        this(name, Translations.PREFIX + "names.categories." + name);
    }

    private ModuleCategory(String name, String translationKey) {
        this.name = name;
        this.translationKey = translationKey;
        this.ordinal = nextOrdinal++;
    }

    /**
     * Built-in categories followed by addon categories in registration order.
     *
     * @return an immutable snapshot; safe to iterate while another thread registers a category
     */
    public static List<ModuleCategory> values() {
        List<ModuleCategory> categories = new ArrayList<>(BUILT_IN.size() + ADDON_CATEGORIES.size());
        categories.addAll(BUILT_IN);
        categories.addAll(ADDON_CATEGORIES);
        return List.copyOf(categories);
    }

    /**
     * Case-insensitive lookup by the stable English name.
     *
     * @return the matching category, or {@code null} when no category uses that name
     */
    public static ModuleCategory findByName(String name) {
        if (name == null) {
            return null;
        }
        String requestedName = name.trim();
        for (ModuleCategory category : BUILT_IN) {
            if (category.name.equalsIgnoreCase(requestedName)) {
                return category;
            }
        }
        for (ModuleCategory category : ADDON_CATEGORIES) {
            if (category.name.equalsIgnoreCase(requestedName)) {
                return category;
            }
        }
        return null;
    }

    /**
     * Registers an addon category. Addons should call
     * {@link evernight.addon.AddonContext#registerCategory(String)} instead, which derives the
     * language key from the addon id.
     *
     * <p>The name is a stable identifier: it is exposed to command arguments and panel identifiers,
     * must be unique (case-insensitively) across built-in and addon categories, and should only use
     * alphanumerics. It cannot be changed after release without breaking user input.
     *
     * @param name           category identifier, trimmed; must not be blank
     * @param translationKey language key resolved on every call; a missing key renders the full key
     * @throws IllegalStateException if the name is blank or already registered
     */
    public static ModuleCategory register(String name, String translationKey) {
        if (name == null) {
            throw new IllegalStateException("Module category name must not be null");
        }
        String categoryName = name.trim();
        if (categoryName.isEmpty()) {
            throw new IllegalStateException("Module category name must not be blank");
        }
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalStateException(
                    "Module category display name key must not be blank: " + categoryName);
        }
        synchronized (ADDON_CATEGORIES) {
            if (findByName(categoryName) != null) {
                throw new IllegalStateException("Module category is already registered: " + categoryName);
            }
            ModuleCategory category = new ModuleCategory(categoryName, translationKey);
            ADDON_CATEGORIES.add(category);
            return category;
        }
    }

    /**
     * Stable English identifier used by commands and panel identity. Never translated.
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Localised category title. Resolved on every call, so switching the language updates the
     * ClickGUI without rebuilding it. Built-in categories look up
     * {@code evernight.names.categories.<Name>}; addon categories use the key supplied at
     * registration time. There is no hard-coded English fallback: a missing key returns the full
     * translation key so the gap is visible and gets fixed in both language files.
     */
    @Override
    public String getDisplayName() {
        return Translations.get(this.translationKey);
    }

    /**
     * Display order: built-in categories keep their historical 0-7 positions, addon categories get
     * the next free index in registration order. Used for ClickGUI panel sorting.
     */
    public int ordinal() {
        return this.ordinal;
    }
}
