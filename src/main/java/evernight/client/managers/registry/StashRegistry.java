package evernight.client.managers.registry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.misc.StashFinder.StashEntry;
import evernight.client.modules.misc.StashFinder.StashFinderModule;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.core.Setting;

import java.util.Collection;
import java.util.Iterator;

public final class StashRegistry extends Registry<StashEntry> implements ClientAccess, JsonConfigurable {
    private static final StashFinderModule stashFinderModule;

    static {
        stashFinderModule = ClientContext.getModule(StashFinderModule.class);
    }

    public StashRegistry() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void fromJson(JsonElement element) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("stashes")) {
            return;
        }
        object = element.getAsJsonObject();
        JsonArray stashes = object.getAsJsonArray("stashes");
        Iterator<JsonElement> iterator = stashes.iterator();
        while (iterator.hasNext()) {
            JsonElement stashElement = iterator.next();
            try {
                Collection<StashEntry> stashEntries = this.entries;
                Gson gson = ClientAccess.gson;
                stashEntries.add(gson.fromJson(stashElement, StashEntry.class));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public JsonElement toJson() {
        Setting<Boolean> logSetting = stashFinderModule.logSetting;
        Boolean loggingEnabled = logSetting.getValue();
        if (!loggingEnabled) {
            return new JsonObject();
        }
        JsonArray stashes = new JsonArray();
        JsonObject object = new JsonObject();
        Iterator<StashEntry> iterator = ClientContext.stashRegistry.getRegistry().iterator();
        while (iterator.hasNext()) {
            StashEntry stash = iterator.next();
            Gson gson = ClientAccess.gson;
            stashes.add(gson.toJsonTree(stash));
        }
        object.add("stashes", stashes);
        return object;
    }

    @Override
    public String getConfigName() {
        return "stashfinder.json";
    }
}