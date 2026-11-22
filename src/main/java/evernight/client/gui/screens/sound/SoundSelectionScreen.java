package evernight.client.gui.screens.sound;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.audio.BuiltinSound;
import evernight.client.gui.components.base.ButtonComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.render.font.FontRenderer;
import evernight.client.settings.core.Setting;
import evernight.client.utils.collections.NamedIdentifier;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import evernight.client.gui.screens.core.ClientScreen;

public class SoundSelectionScreen extends ClientScreen {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final Setting<evernight.client.utils.collections.NamedIdentifier> selectedSetting;
    private final Screen parentScreen;

    public SoundSelectionScreen(Setting<evernight.client.utils.collections.NamedIdentifier> selectedSetting, Screen parentScreen) {
        this.selectedSetting = selectedSetting;
        this.parentScreen = parentScreen;
        this.rebuildSoundPanels();
    }

    @Override
    public void closeWithTransition() {
        if (this.parentScreen instanceof ClientScreen clientScreen) {
            clientScreen.open();
        } else {
            ClientAccess.evernightMinecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public void onFilesDrop(List paths) {
        for (Object pathElement : paths) {
            Path droppedFile = (Path) pathElement;
            if (droppedFile.toFile().getName().endsWith(".ogg")) {
                Path customSoundsDirectory = ConfigurationManager.SOUNDS_DIRECTORY.resolve("custom");
                customSoundsDirectory.toFile().mkdirs();
                try {
                    Files.copy(droppedFile, customSoundsDirectory.resolve(droppedFile.getFileName()));
                } catch (IOException exception) {
                    LOGGER.warn("Could not copy sound file {}", droppedFile.getFileName(), exception);
                }
            }
        }
        ClientContext.soundManager.reload();
        this.rebuildSoundPanels();
    }

    private void rebuildSoundPanels() {
        this.getPanels().clear();
        synchronized (ClientContext.soundManager.getSoundIds()) {
            AtomicInteger panelIndex = new AtomicInteger(0);
            ClientContext.soundManager.getSoundIds().stream()
                    .map(NamedIdentifier::getCategory)
                    .filter(category -> !category.isEmpty())
                    .distinct().forEach(category -> {
                                Panel categoryPanel = new Panel(FontRenderer.capitalize(category));
                                this.getPanels().add(categoryPanel);
                                categoryPanel.setX(categoryPanel.getX() + (categoryPanel.getWidth() + 3)
                                        * panelIndex.getAndIncrement());
                            }
                    );
            for (Object soundElement : ClientContext.soundManager.getSoundIds()) {
                NamedIdentifier soundId = (NamedIdentifier) soundElement;
                for (Panel panel : this.getPanels()) {
                    if (panel.getName().equalsIgnoreCase(soundId.getCategory())
                            && !soundId.equals(BuiltinSound.HORROR.getIdentifier())) {
                        panel.getChildren().add(new SoundOptionButton(this, panel, soundId));
                        break;
                    }
                }
            }
            for (Panel panel : this.getPanels()) {
                panel.getChildren().sort(Comparator.comparing(child -> child instanceof ButtonComponent button
                        ? button.getLabel()
                        : ""
                ));
            }
        }
    }

    public Setting<evernight.client.utils.collections.NamedIdentifier> getSelectedSetting() {
        return this.selectedSetting;
    }
}