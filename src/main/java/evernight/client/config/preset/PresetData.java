package evernight.client.config.preset;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.core.FileUtils;

import java.io.IOException;
import java.nio.file.Path;

public record PresetData(String name, JsonObject contents) implements ClientAccess, NamedEntry {
    @Override
    public String getName() {
        return this.name;
    }

    public void saveToDirectory(Path directory) throws IOException {
        String presetName = this.name;
        Path targetPath = directory.resolve(presetName + ".json");
        Gson gson = ClientAccess.gson;
        JsonObject contents = this.contents();
        String serializedContents = gson.toJson(contents);
        FileUtils.writeUtf8(targetPath, serializedContents);
    }
}