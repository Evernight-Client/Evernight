package evernight.client.render.resource;

import evernight.client.EvernightClient;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.utils.core.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

public final class BundledResourceInstaller {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final HashMap<String, String> resourcePaths = new HashMap<>();

    public BundledResourceInstaller() {
        this.resourcePaths.put("textures/overlay.png", "assets/evernight/textures/overlay.png");
        this.resourcePaths.put("textures/skin_protect.png", "assets/evernight/textures/skin_protect.png");
        this.resourcePaths.put("textures/shine.png", "assets/evernight/textures/shine.png");
        this.resourcePaths.put("shaders/sky.frag", "assets/evernight/shaders/sky/sky.frag");
        this.resourcePaths.put("shaders/custom.frag", "assets/evernight/shaders/custom/example.frag");
    }

    public void installMissingResources() {
        this.resourcePaths.forEach(this::installMissingResource);
    }

    private void installMissingResource(String relativeDestination, String bundledResource) {
        Path destinationPath = ConfigurationManager.ROOT_DIRECTORY.resolve(relativeDestination);
        File destinationFile = destinationPath.toFile();
        if (!destinationFile.exists()) {
            try (InputStream input = EvernightClient.class.getResourceAsStream("/" + bundledResource)) {
                if (input == null) {
                    return;
                }
                byte[] resourceBytes = input.readAllBytes();
                FileUtils.writeBytes(destinationPath, resourceBytes);
            } catch (IOException ioFailure) {
                LOGGER.warn("Could not install bundled resource {}", destinationPath, ioFailure);
            }
        }
    }
}