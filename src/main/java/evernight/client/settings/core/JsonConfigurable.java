package evernight.client.settings.core;

import com.google.gson.JsonElement;

public interface JsonConfigurable {
    void fromJson(JsonElement json);

    JsonElement toJson();

    default String getConfigName() {
        return "";
    }
}