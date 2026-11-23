package evernight.client.managers.registry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import evernight.client.config.preset.PresetData;
import evernight.client.settings.options.PresetCategory;
import evernight.client.settings.options.PresetPrivacyFilter;
import evernight.client.utils.core.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PresetRegistry extends Registry<PresetData> implements ClientAccess {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final PresetCategory category;
    private final Path directory;

    public PresetRegistry(PresetCategory category) {
        this.directory = category.getDirectory();
        this.category = category;
    }

    public boolean applyPreset(String name) {
        synchronized (this.getRegistry()) {
            Iterator<PresetData> presets = this.getRegistry().iterator();
            while (presets.hasNext()) {
                PresetData preset = presets.next();
                String presetName = preset.getName();
                if (presetName.equalsIgnoreCase(name)) {
                    PresetCategory category = this.category;
                    JsonObject contents = preset.contents();
                    category.fromJson(contents);
                    return true;
                }
            }
            return false;
        }
    }

    public PresetData getPreset(String name) {
        synchronized (this.getRegistry()) {
            for (PresetData preset : this.entries) {
                if (preset.getName().equalsIgnoreCase(name)) {
                    return preset;
                }
            }
            return null;
        }
    }

    public void restorePreset(PresetData preset) throws IOException {
        this.restorePreset(preset, preset.getName());
    }

    public void restorePreset(PresetData preset, String replacedName) throws IOException {
        this.writePresetForUndo(preset);
        Path replacedPath = this.directory.resolve(replacedName + ".json");
        Path restoredPath = this.directory.resolve(preset.getName() + ".json");
        if (!replacedName.equals(preset.getName()) && Files.exists(replacedPath)
                && !Files.isSameFile(replacedPath, restoredPath)) {
            Files.delete(replacedPath);
        }
        this.entries.removeIf(candidate -> candidate.getName().equalsIgnoreCase(preset.getName()));
        this.entries.add(preset);
    }

    private void writePresetForUndo(PresetData preset) throws IOException {
        Path temporary = Files.createTempFile(this.directory, ".evernight-preset-", ".tmp");
        try {
            FileUtils.writeUtf8(temporary, ClientAccess.gson.toJson(preset.contents()));
            Files.move(temporary, this.directory.resolve(preset.getName() + ".json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void deletePresetForUndo(String name) throws IOException {
        Path path = this.directory.resolve(name + ".json");
        if (Files.isDirectory(path)) {
            throw new IOException("Preset path is a directory: " + path.getFileName());
        }
        Files.deleteIfExists(path);
        this.entries.removeIf(candidate -> candidate.getName().equalsIgnoreCase(name));
    }

    public void renamePresetForUndo(String sourceName, String targetName, PresetData previousTarget, PresetData source) throws IOException {
        if (source == null) {
            throw new IOException("Preset no longer exists: " + sourceName);
        }
        Path sourcePath = this.directory.resolve(sourceName + ".json");
        Path targetPath = this.directory.resolve(targetName + ".json");
        if (!Files.isRegularFile(sourcePath)) {
            throw new IOException("Preset file no longer exists: " + sourcePath.getFileName());
        }
        Files.move(sourcePath, targetPath);
        try {
            if (previousTarget != null) {
                this.writePresetForUndo(previousTarget);
            }
        } catch (IOException failure) {
            try {
                Files.move(targetPath, sourcePath);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        this.entries.removeIf(candidate -> candidate.getName().equalsIgnoreCase(sourceName));
        this.entries.add(new PresetData(targetName, source.contents()));
        if (previousTarget != null) {
            this.entries.add(previousTarget);
        }
    }

    public void savePreset(String name) throws IOException {
        this.entries.removeIf(candidate -> candidate.getName().equalsIgnoreCase(name));
        PresetData preset = new PresetData(name, this.category.toJson().getAsJsonObject());
        preset.saveToDirectory(this.directory);
        this.entries.add(preset);
    }

    public boolean deletePreset(String name) {
        File file = this.directory.resolve(name + ".json").toFile();
        file.delete();
        return this.entries.removeIf(candidate -> candidate.getName().equalsIgnoreCase(name));
    }

    public boolean renamePreset(String oldName, String newName) {
        if (oldName.equalsIgnoreCase(newName)) {
            return false;
        }
        File sourceFile = this.directory.resolve(oldName + ".json").toFile();
        if (sourceFile.exists()) {
            File fileToRename = this.directory.resolve(oldName + ".json").toFile();
            File targetFile = this.directory.resolve(newName + ".json").toFile();
            fileToRename.renameTo(targetFile);
        }
        return this.copyPreset(oldName, newName)
                && this.entries.removeIf(candidate -> candidate.getName().equalsIgnoreCase(oldName));
    }

    public void loadPresets() {
        synchronized (this.getRegistry()) {
            this.entries.clear();
            try {
                File directoryFile = this.directory.toFile();
                File[] files = directoryFile.listFiles();
                for (File file : Objects.requireNonNull(files)) {
                    String fileName = file.getName();
                    if (fileName.endsWith(".json") && file.length() > 0L) {
                        try {
                            String jsonText = FileUtils.readUtf8(file.toPath());
                            JsonObject contents = JsonParser.parseString(jsonText).getAsJsonObject();
                            if (contents != null) {
                                // Old preset files may still contain credential-bearing modules;
                                // scrub them so sharing the presets folder cannot leak them.
                                if (this.category != PresetCategory.MACRO
                                        && PresetPrivacyFilter.INSTANCE.removeExcludedModules(contents)) {
                                    try {
                                        FileUtils.writeUtf8(file.toPath(), ClientAccess.gson.toJson(contents));
                                    } catch (IOException scrubFailure) {
                                        LOGGER.warn("Could not scrub preset {}", file.getName(), scrubFailure);
                                    }
                                }
                                Collection<PresetData> loadedPresets = this.entries;
                                loadedPresets.add(new PresetData(file.getName().replace(".json", ""), contents));
                            }
                        } catch (Throwable entryFailure) {
                            LOGGER.warn("Could not load preset {}", file.getName(), entryFailure);
                        }
                    }
                }
            } catch (Exception directoryFailure) {
                LOGGER.warn("Could not read preset directory {}", this.directory, directoryFailure);
            }
        }
    }

    public boolean copyPreset(String sourceName, String targetName) {
        Iterator<PresetData> presets = this.entries.iterator();
        while (presets.hasNext()) {
            PresetData preset = presets.next();
            String presetName = preset.getName();
            if (presetName.equalsIgnoreCase(sourceName)) {
                try {
                    Path copyDirectory = this.directory;
                    Gson gson = ClientAccess.gson;
                    JsonObject contents = preset.contents();
                    String serializedContents = gson.toJson(contents);
                    FileUtils.writeUtf8(copyDirectory.resolve(targetName + ".json"), serializedContents);
                    Collection<PresetData> copiedPresets = this.entries;
                    copiedPresets.add(new PresetData(targetName, preset.contents()));
                    return true;
                } catch (IOException writeFailure) {
                    LOGGER.warn("Could not copy preset {}", targetName, writeFailure);
                    return false;
                }
            }
        }
        return false;
    }

    public void savePresetByExactName(String name) throws IOException {
        Iterator<PresetData> presets = this.entries.iterator();
        while (presets.hasNext()) {
            PresetData preset = presets.next();
            if (preset.getName().equals(name)) {
                preset.saveToDirectory(this.directory);
            }
        }
    }

    public void forEachPreset(Consumer<PresetData> consumer) {
        synchronized (this.getRegistry()) {
            this.getRegistry().forEach(consumer);
        }
    }

    public void saveAllPresets() {
        Iterator<PresetData> presets = this.entries.iterator();
        while (presets.hasNext()) {
            PresetData preset = presets.next();
            try {
                preset.saveToDirectory(this.directory);
            } catch (IOException writeFailure) {
                LOGGER.warn("Could not save preset {}", preset.getName(), writeFailure);
            }
        }
    }
}