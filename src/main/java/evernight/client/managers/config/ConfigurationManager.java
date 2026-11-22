package evernight.client.managers.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import evernight.client.commands.control.Panic.PanicCommand;
import evernight.client.config.legacy.LegacySocialMigration;
import evernight.client.config.preset.ModulePresetCodec;
import evernight.client.config.preset.PresetUndoState;
import evernight.client.render.resource.BundledResourceInstaller;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.options.PresetCategory;
import evernight.client.utils.core.FileUtils;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import evernight.client.config.legacy.ClientDataDirectory;
import evernight.client.managers.registry.ConfigurableRegistry;
import evernight.client.managers.registry.PresetRegistry;

public final class ConfigurationManager implements ClientAccess {
    public static final Path ROOT_DIRECTORY;
    public static final Path PRESETS_DIRECTORY;
    public static final Path SPAMMER_DIRECTORY;
    public static final Path KILLSAY_DIRECTORY;
    public static final Path TEXTURES_DIRECTORY;
    public static final Path MAPS_DIRECTORY;
    public static final Path CHUNKS_DIRECTORY;
    public static final Path SOUNDS_DIRECTORY;
    public static final Path SHADERS_DIRECTORY;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final List<Path> directories;

    static {
        Path parentDirectory = FabricLoader.getInstance().getGameDir();
        ROOT_DIRECTORY = evernight.client.config.legacy.ClientDataDirectory.migrateLegacy(parentDirectory.resolve("evernight"));
        parentDirectory = ROOT_DIRECTORY;
        PRESETS_DIRECTORY = parentDirectory.resolve("presets");
        parentDirectory = ROOT_DIRECTORY;
        SPAMMER_DIRECTORY = parentDirectory.resolve("spammer");
        parentDirectory = ROOT_DIRECTORY;
        KILLSAY_DIRECTORY = parentDirectory.resolve("killsay");
        parentDirectory = ROOT_DIRECTORY;
        TEXTURES_DIRECTORY = parentDirectory.resolve("textures");
        parentDirectory = ROOT_DIRECTORY;
        MAPS_DIRECTORY = parentDirectory.resolve("maps");
        parentDirectory = ROOT_DIRECTORY;
        CHUNKS_DIRECTORY = parentDirectory.resolve("chunks");
        parentDirectory = ROOT_DIRECTORY;
        SOUNDS_DIRECTORY = parentDirectory.resolve("sounds");
        parentDirectory = ROOT_DIRECTORY;
        SHADERS_DIRECTORY = parentDirectory.resolve("shaders");
        directories = new ArrayList<>();
    }

    private final ModulePresetCodec modulePresetCodec = new ModulePresetCodec();
    private final Map<PresetCategory, PresetRegistry> presetRegistries = new HashMap<>();
    private final BundledResourceInstaller bundledResources = new BundledResourceInstaller();
    private final PresetUndoState presetUndoState = new PresetUndoState();
    private final ConfigurableRegistry configurables = new ConfigurableRegistry();

    public ModulePresetCodec getModulePresetCodec() {
        return this.modulePresetCodec;
    }

    public PresetUndoState getPresetUndoState() {
        return this.presetUndoState;
    }

    /**
     * Registers an extra configuration file, used by addons for state that does not fit into a module.
     * Register before {@link #load()} for the existing file to be read back.
     */
    public void registerConfigurable(JsonConfigurable configurable) {
        this.configurables.register(configurable);
    }

    public void load() {
        directories.clear();
        directories.addAll(List.of(ROOT_DIRECTORY, PRESETS_DIRECTORY, SPAMMER_DIRECTORY, KILLSAY_DIRECTORY,
                TEXTURES_DIRECTORY, MAPS_DIRECTORY, SOUNDS_DIRECTORY, SHADERS_DIRECTORY, CHUNKS_DIRECTORY));
        for (PresetCategory category : PresetCategory.values()) directories.add(category.getDirectory());
        this.prepareDirectories();
        try {
            LegacySocialMigration.migrate();
        } catch (Throwable ignoredBootstrapFailure) {
        }
        Iterator<?> configurableIterator = this.configurables.iterator();
        while (configurableIterator.hasNext()) {
            JsonConfigurable configurable = (JsonConfigurable) configurableIterator.next();
            try {
                Path configPath = ROOT_DIRECTORY.resolve(configurable.getConfigName());
                File configFile = configPath.toFile();
                if (configFile.exists()) {
                    String configText = FileUtils.readUtf8(configPath);
                    JsonElement configJson = JsonParser.parseString(configText);
                    configurable.fromJson(configJson.getAsJsonObject());
                }
            } catch (Throwable loadFailure) {
                LOGGER.warn("Could not load config {}", configurable.getConfigName(), loadFailure);
            }
        }
        for (PresetCategory presetCategory : PresetCategory.values()) {
            PresetRegistry presetRegistry = new PresetRegistry(presetCategory);
            this.presetRegistries.put(presetCategory, presetRegistry);
            presetRegistry.loadPresets();
        }
    }

    public void prepareDirectories() {
        try {
            Path legacyAutoEzPath = ROOT_DIRECTORY.resolve("autoez");
            File legacyAutoEz = legacyAutoEzPath.toFile();
            if (legacyAutoEz.isDirectory()) {
                File killsayTarget = KILLSAY_DIRECTORY.toFile();
                legacyAutoEz.renameTo(killsayTarget);
            }
        } catch (Exception ignoredMigrationFailure) {
        }
        for (Path path : directories) {
            try {
                Files.createDirectories(path);
            } catch (IOException failure) {
                ClientAccess.logger.warn("Could not create configuration directory {}", path.getFileName(), failure);
            }
        }
        this.bundledResources.installMissingResources();
    }

    public PresetRegistry getPresetRegistry(PresetCategory category) {
        return this.presetRegistries.get(category);
    }

    public void save() {
        if (!PanicCommand.unloaded) {
            Iterator<?> configurableIterator = this.configurables.iterator();
            while (configurableIterator.hasNext()) {
                JsonConfigurable configurable = (JsonConfigurable) configurableIterator.next();
                try {
                    Path configPath = ROOT_DIRECTORY.resolve(configurable.getConfigName());
                    Gson gsonSnapshot = ClientAccess.gson;
                    JsonElement serialized = configurable.toJson();
                    JsonObject object = serialized.getAsJsonObject();
                    FileUtils.writeUtf8(configPath, gsonSnapshot.toJson(object));
                } catch (Throwable saveFailure) {
                    LOGGER.warn("Could not save config {}", configurable.getConfigName(), saveFailure);
                }
            }
        }
    }
}