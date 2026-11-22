package evernight.client.gui.screens.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.config.legacy.LegacyBranding;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.base.ButtonComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.PresetCategory;
import evernight.client.utils.core.FileUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import evernight.client.gui.screens.core.ClientScreen;

public final class PresetImportScreen extends ClientScreen {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final PresetCategory detectedCategory;
    private final Panel importPanel;
    private final Screen returnScreen;
    private final Path sourceFile;

    public PresetImportScreen(Screen previousScreen, Path sourcePath) {
        this.returnScreen = previousScreen;
        this.detectedCategory = this.readPresetCategory(sourcePath);
        this.importPanel = new Panel("Preset", () -> this.getCategoryTitle(this.detectedCategory));
        this.sourceFile = sourcePath;
        this.importPanel.getChildren().add(new ButtonComponent(this.importPanel,
                () -> Translations.ui("hud.PresetPrefix") + this.getPresetName(sourcePath)
        ));
        if (this.detectedCategory != null) {
            this.importPanel.registerChild(new ActionButtonComponent(this.importPanel,
                    Translations.ui("gui.Import"),
                    () -> this.importPreset(sourcePath, this.detectedCategory)));
        } else {
            for (PresetCategory category : PresetCategory.values()) {
                this.importPanel.registerChild(new ActionButtonComponent(this.importPanel,
                        category.getDisplayName(), () -> this.importPreset(sourcePath, category)));
            }
        }
        this.importPanel.registerChild(new ActionButtonComponent(this.importPanel,
                Translations.ui("gui.Cancel"),
                this::closeWithTransition));
        this.getPanels().add(this.importPanel);
    }

    public static boolean openForDroppedFiles(Screen previousScreen, List droppedPaths) {
        ArrayList jsonPaths = new ArrayList();
        droppedPaths.stream().filter(path -> path.toString().endsWith(".json")).forEach(jsonPaths::add);
        if (jsonPaths.size() != 1) {
            return false;
        }
        ClientAccess.evernightMinecraft.setScreen(
                new PresetImportScreen(previousScreen, (Path) jsonPaths.getFirst()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 && this.detectedCategory != null) {
            this.importPreset(this.sourceFile, this.detectedCategory);
        }
        return super.keyPressed(event);
    }

    @Override
    public void closeWithTransition() {
        ClientAccess.evernightMinecraft.setScreen(this.returnScreen);
    }

    private String getPresetName(Path sourcePath) {
        return sourcePath.toFile().getName().replace(".json", "");
    }

    private void importPreset(Path sourcePath, PresetCategory targetCategory) {
        String fileName = sourcePath.toFile().getName();
        Path targetPath = targetCategory.getDirectory().resolve(fileName);
        try {
            JsonObject contents = this.readPresetObject(sourcePath);
            if (contents != null && LegacyBranding.isLegacyPreset(contents)) {
                // Presets exported by the predecessor client ("mio-fabric") are still accepted: rewrite
                // only the client tag so the saved file matches a native Evernight preset.
                JsonObject normalized = LegacyBranding.normalizePresetMetadata(contents);
                FileUtils.writeUtf8(targetPath, ClientAccess.gson.toJson(normalized));
            } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException copyFailure) {
            LOGGER.warn("Could not import preset file {}", fileName, copyFailure);
        }
        ClientContext.configurationManager.getPresetRegistry(targetCategory).loadPresets();
        ClientAccess.evernightMinecraft.setScreen(this.returnScreen);
    }

    private PresetCategory readPresetCategory(Path sourcePath) {
        JsonObject jsonObject = this.readPresetObject(sourcePath);
        if (jsonObject == null || !jsonObject.has("category")) {
            return null;
        }
        JsonElement categoryElement = jsonObject.get("category");
        if (!categoryElement.isJsonPrimitive() || !categoryElement.getAsJsonPrimitive().isString()) {
            return null;
        }
        return PresetCategory.findByName(categoryElement.getAsString());
    }

    /**
     * Reads a dropped preset file defensively: a leading UTF-8 BOM is stripped (Gson rejects it) and
     * malformed JSON returns {@code null} instead of escaping the screen constructor, so the import
     * dialog can still fall back to the manual category picker.
     */
    private JsonObject readPresetObject(Path sourcePath) {
        try {
            String jsonText = Files.readString(sourcePath);
            if (jsonText.startsWith("\uFEFF")) {
                jsonText = jsonText.substring(1);
            }
            JsonElement parsedJson = JsonParser.parseString(jsonText);
            return parsedJson.isJsonObject() ? parsedJson.getAsJsonObject() : null;
        } catch (IOException | RuntimeException readFailure) {
            return null;
        }
    }

    private String getCategoryTitle(PresetCategory category) {
        return category == null ? Translations.ui("gui.Unknown") : category.getDisplayName();
    }
}