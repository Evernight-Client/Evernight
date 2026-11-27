package evernight.gametest;

import evernight.client.ClientContext;
import evernight.client.integrations.litematica.LitematicaProjectionSource;
import evernight.client.integrations.litematica.ProjectionSource;
import evernight.client.modules.client.Baritone.BaritoneModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashSet;
import java.util.Set;

/**
 * G1: the real client must finish Evernight initialization with the complete compatibility
 * environment loaded and the mod-dependent bridges/modules available.
 */
public final class EvernightClientGameTest implements FabricClientGameTest {
    private static final String[] OPTIONAL_MODS = {
            "viafabricplus",
            "entityculling",
            "sodium",
            "lithium",
            "immediatelyfast",
            "xaerominimap",
            "litematica",
            "malilib"
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            FabricLoader loader = FabricLoader.getInstance();

            require(loader.isModLoaded("fabric-api"), "Fabric API is not loaded");
            require(loader.getModContainer("minecraft")
                    .map(container -> "1.21.11".equals(container.getMetadata().getVersion().getFriendlyString()))
                    .orElse(false), "Minecraft 1.21.11 is required");
            require(loader.isModLoaded("baritone") || loader.isModLoaded("baritone-meteor"),
                    "Baritone is not loaded");
            for (String modId : OPTIONAL_MODS) {
                require(loader.isModLoaded(modId), "Compatibility mod is not loaded: " + modId);
            }

            require(ClientContext.moduleRegistry != null, "ModuleRegistry is not initialized");
            require(ClientContext.commandRegistry != null, "CommandRegistry is not initialized");
            require(ClientContext.configurationManager != null, "ConfigurationManager is not initialized");
            require(ClientContext.languageTable != null, "LanguageTable is not initialized");
            require(ClientContext.languageTable.size() > 0, "LanguageTable is empty");
            require(ClientContext.baritoneBridge != null && ClientContext.baritoneBridge.isAvailable(),
                    "Baritone bridge is unavailable");
            require(ClientContext.waypointAdapter != null && ClientContext.waypointAdapter.isAvailable(),
                    "Xaero waypoint adapter is unavailable");
            require(ProjectionSource.isLitematicaLoaded(), "Litematica is not loaded");
            require(ClientContext.projectionSource instanceof LitematicaProjectionSource,
                    "Litematica projection source was not selected");

            Set<String> configNames = new HashSet<>();
            for (evernight.client.modules.Module module : ClientContext.moduleRegistry.getRegistry()) {
                require(configNames.add(module.getConfigName()),
                        "Duplicate module config name: " + module.getConfigName());
            }
            require(configNames.size() > 50, "Only " + configNames.size() + " modules registered");

            BaritoneModule baritone = ClientContext.getModule(BaritoneModule.class);
            require(baritone != null, "BaritoneModule is not registered");
            require(baritone.isAvailable(), "BaritoneModule is unavailable although Baritone is loaded");
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
