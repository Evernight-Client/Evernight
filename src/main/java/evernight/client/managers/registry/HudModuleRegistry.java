package evernight.client.managers.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.ClientInitializationEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Module;
import evernight.client.modules.hud.*;
import evernight.client.settings.core.JsonConfigurable;

import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import evernight.client.modules.hud.Armor.ArmorModule;
import evernight.client.modules.hud.Chat.ChatModule;
import evernight.client.modules.hud.Crypto.CryptoModule;
import evernight.client.modules.hud.Direction.DirectionModule;
import evernight.client.modules.hud.Effects.EffectsModule;
import evernight.client.modules.hud.EntityList.EntityListModule;
import evernight.client.modules.hud.Graph.GraphModule;
import evernight.client.modules.hud.Indicator.IndicatorModule;
import evernight.client.modules.hud.Inventory.InventoryModule;
import evernight.client.modules.hud.LagOMeter.LagOMeterModule;
import evernight.client.modules.hud.Map.MapModule;
import evernight.client.modules.hud.Metrics.MetricsModule;
import evernight.client.modules.hud.ModuleList.ModuleListModule;
import evernight.client.modules.hud.Music.MusicModule;
import evernight.client.modules.hud.PlayerModel.PlayerModelModule;
import evernight.client.modules.hud.Position.PositionModule;
import evernight.client.modules.hud.TextRadar.TextRadarModule;
import evernight.client.modules.hud.Totems.TotemsModule;
import evernight.client.modules.hud.Watermark.WatermarkModule;
import evernight.client.modules.hud.Welcomer.WelcomerModule;

public final class HudModuleRegistry implements ClientAccess, JsonConfigurable {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public HudModuleRegistry() {
        EventBus events = ClientAccess.eventBus;
        events.subscribe(this);
        ModuleRegistry watermarkRegistry = ClientContext.moduleRegistry;
        WatermarkModule watermark = new WatermarkModule();
        watermarkRegistry.registerModule(watermark);
        ClientContext.moduleRegistry.registerModule(new ModuleListModule());
        ClientContext.moduleRegistry.registerModule(new ArmorModule());
        ClientContext.moduleRegistry.registerModule(new GraphModule());
        ClientContext.moduleRegistry.registerModule(new EffectsModule());
        ClientContext.moduleRegistry.registerModule(new WelcomerModule());
        ClientContext.moduleRegistry.registerModule(new PositionModule());
        ClientContext.moduleRegistry.registerModule(new DirectionModule());
        ClientContext.moduleRegistry.registerModule(new TextRadarModule());
        ClientContext.moduleRegistry.registerModule(new MetricsModule());
        ClientContext.moduleRegistry.registerModule(new TotemsModule());
        ClientContext.moduleRegistry.registerModule(new LagOMeterModule());
        ClientContext.moduleRegistry.registerModule(new InventoryModule());
        ClientContext.moduleRegistry.registerModule(new MusicModule());
        ClientContext.moduleRegistry.registerModule(new PlayerModelModule());
        ClientContext.moduleRegistry.registerModule(new EntityListModule());
        ClientContext.moduleRegistry.registerModule(new ChatModule());
        ClientContext.moduleRegistry.registerModule(new MapModule());
        ClientContext.moduleRegistry.registerModule(new CryptoModule());
        ClientContext.moduleRegistry.registerModule(new IndicatorModule());
    }

    private static Module findConfiguredModule(Entry configurationEntry) {
        ModuleRegistry registry = ClientContext.moduleRegistry;
        Predicate<Module> matchesName = candidate -> {
            String configName = candidate.getConfigName();
            String entryName = (String) configurationEntry.getKey();
            return configName.equalsIgnoreCase(entryName);
        };
        Optional match = registry.findFirst(matchesName);
        return (Module) match.orElse(null);
    }

    @Override
    public void fromJson(JsonElement json) {
        JsonObject configuration = json.getAsJsonObject();
        configuration.entrySet().stream().sorted(Comparator.comparing(entry -> {
                    try {
                        JsonElement moduleJson = entry.getValue();
                        JsonObject moduleConfiguration = moduleJson.getAsJsonObject();
                        String hudKey = "hud";
                        JsonObject hudConfiguration = moduleConfiguration.getAsJsonObject(hudKey);
                        String indexKey = "index";
                        JsonElement indexJson = hudConfiguration.get(indexKey);
                        int index = indexJson.getAsInt();
                        return index;
                    } catch (Throwable failure) {
                        return Integer.valueOf(0);
                    }
                })).map(entry -> new SimpleEntry<>(findConfiguredModule(entry), entry.getValue()))
                .filter(entry -> entry.getKey() instanceof HudWidgetModule).forEach(entry -> {
                    Module module = entry.getKey();
                    try {
                        module.fromJson(entry.getValue());
                    } catch (Exception failure) {
                        LOGGER.warn("Could not load HUD module {}", module.getConfigName(), failure);
                    }
                });
    }

    @Override
    public JsonElement toJson() {
        JsonObject configuration = new JsonObject();
        ModuleRegistry registry = ClientContext.moduleRegistry;
        Iterator modules = registry.getRegistry().iterator();
        while (modules.hasNext()) {
            Module module = (Module) modules.next();
            if (module instanceof HudWidgetModule) {
                try {
                    String configName = module.getConfigName();
                    JsonElement moduleJson = module.toJson();
                    configuration.add(configName, moduleJson);
                } catch (Exception failure) {
                    LOGGER.warn("Could not save HUD module {}", module.getConfigName(), failure);
                }
            }
        }
        return configuration;
    }

    @Override
    public String getConfigName() {
        return "hud.json";
    }

    @EventHandler
    private void refreshHudAnchors(ClientInitializationEvent event) {
        ModuleRegistry registry = ClientContext.moduleRegistry;
        Iterator modules = registry.getRegistry().iterator();
        while (modules.hasNext()) {
            Module module = (Module) modules.next();
            if (module instanceof HudWidgetModule widget) {
                widget.setAnchor(widget.getAnchor());
            }
        }
    }
}