package evernight.client.managers.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.settings.core.JsonConfigurable;

/**
 * Standalone credential store for {@code ExtraScreenshot}.
 *
 * <p>Imgur uploads need a client ID or a Bearer access token. Keeping it in the module settings would
 * write it into {@code modules.json} and into every exported preset, so the value lives in its own
 * {@code screenshot.json} instead. The module setting is excluded from config serialization and is
 * synchronised with this store in both directions.
 */
public final class ScreenshotCredentialStore implements JsonConfigurable {
    /** Bundled public Client ID, used until the player configures their own credential. */
    public static final String DEFAULT_IMGUR_CLIENT_ID = "e7c0b6a4c926098";

    private String clientId = DEFAULT_IMGUR_CLIENT_ID;
    private boolean loadedFromFile;

    public boolean isLoadedFromFile() {
        return this.loadedFromFile;
    }

    public String getClientId() {
        return this.clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId;
    }

    @Override
    public void fromJson(JsonElement json) {
        this.loadedFromFile = true;
        if (!(json instanceof JsonObject object) || !object.has("clientId")) {
            return;
        }
        JsonElement stored = object.get("clientId");
        if (stored != null && !stored.isJsonNull()) {
            this.clientId = stored.getAsString();
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("clientId", this.clientId);
        return json;
    }

    @Override
    public String getConfigName() {
        return "screenshot.json";
    }
}
