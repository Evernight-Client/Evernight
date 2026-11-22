package evernight.client.gui.screens.preset;

import com.google.gson.JsonObject;
import evernight.client.ClientAccess;
import evernight.client.gui.core.GuiElement;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.base.ButtonComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.modules.Module;
import evernight.client.render.text.Translations;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import evernight.client.gui.screens.core.ClientScreen;

public class ModuleImportConfirmationScreen extends ClientScreen {
    private final Screen returnScreen;
    private final Module module;
    private final JsonObject moduleJson;

    public ModuleImportConfirmationScreen(Screen previousScreen, Module targetModule, JsonObject importedJson) {
        this.returnScreen = previousScreen;
        this.module = targetModule;
        this.moduleJson = importedJson;
        Panel confirmationPanel = new Panel("Confirmation");
        ButtonComponent moduleLabel = new ButtonComponent(confirmationPanel,
                () -> Translations.ui("hud.ModulePrefix") + targetModule.getDisplayName()
        );
        ActionButtonComponent importButton = new ActionButtonComponent(
                confirmationPanel, "Import", this::importModule
        );
        ActionButtonComponent cancelButton = new ActionButtonComponent(
                confirmationPanel, "Cancel", this::closeWithTransition
        );
        GuiElement[] children = {moduleLabel, importButton, cancelButton};
        confirmationPanel.addChildren(children);
        this.getPanels().add(confirmationPanel);
    }

    private void importModule() {
        this.module.fromJson(this.moduleJson);
        this.closeWithTransition();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257) {
            this.importModule();
        }
        return super.keyPressed(event);
    }

    @Override
    public void closeWithTransition() {
        if (this.returnScreen instanceof ClientScreen clientScreen) {
            clientScreen.open();
        } else {
            ClientAccess.evernightMinecraft.setScreen(this.returnScreen);
        }
    }
}