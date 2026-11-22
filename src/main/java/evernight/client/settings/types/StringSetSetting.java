package evernight.client.settings.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.*;
import java.util.function.Predicate;

public final class StringSetSetting extends SetSetting<String> {
    public StringSetSetting(String name, String... defaultEntries) {
        super(name, defaultEntries);
    }

    public StringSetSetting(String name, Predicate<? super Set<String>> visibility, String... defaultEntries) {
        super(name, visibility, defaultEntries);
    }

    @Override
    public String parseEntry(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    @Override
    public Collection<String> getAvailableEntries() {
        return Collections.emptyList();
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonArray()) {
            Set<String> values = this.getValue();
            values.clear();
            JsonArray array = json.getAsJsonArray();
            Iterator entries = array.iterator();
            while (entries.hasNext()) {
                JsonElement entry = (JsonElement) entries.next();
                String text = entry.getAsString();
                values.add(text);
            }
        }
    }

    @Override
    public String formatEntry(String entry) {
        return entry.toLowerCase(Locale.ROOT);
    }
}