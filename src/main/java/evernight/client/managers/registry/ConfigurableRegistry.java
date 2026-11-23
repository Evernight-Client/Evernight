package evernight.client.managers.registry;

import evernight.client.ClientContext;
import evernight.client.settings.core.JsonConfigurable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ConfigurableRegistry implements Iterable<JsonConfigurable> {
    private final List<JsonConfigurable> configurables = new ArrayList<>();

    public ConfigurableRegistry() {
        this.register(ClientContext.webhookManager);
        this.register(ClientContext.screenshotCredentialStore);
        this.register(ClientContext.moduleRegistry);
        this.register(ClientContext.socialRegistry);
        this.register(ClientContext.waypointRegistry);
        this.register(ClientContext.macroRegistry);
        this.register(ClientContext.stashRegistry);
        this.register(ClientContext.hudModuleRegistry);
        this.register(ClientContext.kitRegistry);
        this.register(ClientContext.autoPresetRegistry);
        this.register(ClientContext.aliasRegistry);
        this.register(ClientContext.chatFilterRegistry);
        this.register(ClientContext.glintRegistry);
        this.register(ClientContext.commandRegistry);
        this.register(ClientContext.tradeRegistry);
        this.register(ClientContext.blockColorRegistry);
    }

    @Override
    public Iterator<JsonConfigurable> iterator() {
        return this.configurables.iterator();
    }

    /**
     * Adds a configuration source. Called by the constructor for the built-in registries and by addons
     * through {@link evernight.addon.AddonContext#registerConfig(evenight.client.settings.core.JsonConfigurable)}.
     */
    public void register(JsonConfigurable configurable) {
        this.configurables.add(configurable);
    }
}