package evernight.client.settings.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import evernight.client.settings.core.Setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Ordered list of raw text entries that is edited in the ClickGUI.
 *
 * <p>Unlike {@link StringSetSetting}, the order and the exact text are preserved: spammer messages
 * and kill say lines are sent in sequence, so the player must be able to reorder and rewrite them.
 * The list is mutated in place; every mutation runs the setting change callback so live consumers
 * (the module or its message helper) can refresh their cached view.
 */
public final class StringListSetting extends Setting<List<String>> {
    private Runnable prepareAction;
    private Runnable resetAction;

    public StringListSetting(String name, String... defaultEntries) {
        super(name, new ArrayList<>(List.of(defaultEntries)));
        this.setValue(new ArrayList<>(this.getDefaultValue()));
    }

    /**
     * Registers a callback that runs right before the editor opens, so file-backed lists can reload
     * their contents instead of editing a stale snapshot.
     */
    public StringListSetting withPrepareAction(Runnable prepareAction) {
        this.prepareAction = prepareAction;
        return this;
    }

    public void prepare() {
        if (this.prepareAction != null) {
            this.prepareAction.run();
        }
    }

    /**
     * Replaces the default reset behaviour. File-backed lists use it to reload their file instead of
     * clearing the list (and therefore the file) when the player resets all module settings.
     */
    public StringListSetting withResetAction(Runnable resetAction) {
        this.resetAction = resetAction;
        return this;
    }

    @Override
    public void reset() {
        if (this.resetAction != null) {
            this.resetAction.run();
            return;
        }
        this.replaceEntries(this.getDefaultValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonArray()) {
            return;
        }
        List<String> entries = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray()) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            entries.add(element.getAsString());
        }
        this.replaceEntries(entries);
    }

    @Override
    public JsonElement toJson() {
        JsonArray json = new JsonArray();
        for (String entry : this.getValue()) {
            json.add(entry);
        }
        return json;
    }

    @Override
    public void parseValue(String text) {
        if (text != null) {
            this.addEntry(text);
        }
    }

    /** Replaces every entry, used by config loading and file imports. */
    public void replaceEntries(Collection<String> entries) {
        List<String> values = this.getValue();
        values.clear();
        if (entries != null) {
            values.addAll(entries);
        }
        this.runChangeCallback();
    }

    public void addEntry(String entry) {
        if (entry != null) {
            this.getValue().add(entry);
            this.runChangeCallback();
        }
    }

    public void setEntry(int index, String entry) {
        List<String> values = this.getValue();
        if (entry == null || index < 0 || index >= values.size()) {
            return;
        }
        values.set(index, entry);
        this.runChangeCallback();
    }

    public void removeEntry(int index) {
        List<String> values = this.getValue();
        if (index < 0 || index >= values.size()) {
            return;
        }
        values.remove(index);
        this.runChangeCallback();
    }

    public void moveEntry(int fromIndex, int toIndex) {
        List<String> values = this.getValue();
        if (fromIndex < 0 || fromIndex >= values.size() || toIndex < 0 || toIndex >= values.size()
                || fromIndex == toIndex) {
            return;
        }
        String entry = values.remove(fromIndex);
        values.add(toIndex, entry);
        this.runChangeCallback();
    }

    public void clearEntries() {
        List<String> values = this.getValue();
        if (values.isEmpty()) {
            return;
        }
        values.clear();
        this.runChangeCallback();
    }
}
