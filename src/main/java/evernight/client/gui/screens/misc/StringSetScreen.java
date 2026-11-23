package evernight.client.gui.screens.misc;

import evernight.client.ClientAccess;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.setting.StringSettingComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.settings.types.StringSetting;
import evernight.client.settings.types.StringSetSetting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game editor for a {@link StringSetSetting}.
 *
 * <p>The left panel lists the current entries: each row is a button carrying the entry text, and
 * clicking it removes that entry. The right panel holds the text field for a new entry plus the add
 * and clear buttons, so the list can be maintained without touching a command or the config file.
 * Pressing Enter inside the text field adds the entry as well.
 */
public final class StringSetScreen extends ClientScreen {
    private final StringSetSetting stringSetSetting;
    private final Screen parentScreen;
    private final Panel entriesPanel = new Panel("List");
    private final Panel actionPanel = new Panel("Extra");
    private final StringSetting draftSetting = new StringSetting("Word", "");
    private final StringSettingComponent draftComponent;

    public StringSetScreen(Screen parentScreen, StringSetSetting stringSetSetting) {
        this.stringSetSetting = stringSetSetting;
        this.parentScreen = parentScreen;
        this.draftComponent = new StringSettingComponent(this.actionPanel, CloseState.ALWAYS_OPEN,
                this.draftSetting);
        this.actionPanel.registerChild(this.draftComponent);
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Add", this::addDraft));
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Clear", this::clearEntries));
        this.actionPanel.setX(this.entriesPanel.getX() + this.entriesPanel.getWidth() + (4));
        this.panels.add(this.entriesPanel);
        this.panels.add(this.actionPanel);
        this.rebuildEntries();
    }

    /**
     * Recreates one row per entry so removals and additions are reflected immediately.
     */
    private void rebuildEntries() {
        this.entriesPanel.getRegistry().clear();
        List<String> entries = new ArrayList<>(this.stringSetSetting.getValue());
        entries.sort(String.CASE_INSENSITIVE_ORDER);
        for (String entry : entries) {
            this.entriesPanel.registerChild(new ActionButtonComponent(this.entriesPanel, () -> entry,
                    () -> this.removeEntry(entry), false));
        }
    }

    private void addDraft() {
        // The text field keeps typed characters in its own buffer until Enter commits them, so the
        // setting value is still empty here; read the live text instead (works for the add button too,
        // which is clicked while the field is still being edited).
        String text = this.draftComponent.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        this.stringSetSetting.addEntry(this.stringSetSetting.parseEntry(text.trim()));
        this.draftSetting.setValue("");
        // beginEditing() only snapshots the setting while the row is not already editing, so leave
        // edit mode first to clear the buffer, then re-enter it ready for the next entry.
        this.draftComponent.editing = false;
        this.rebuildEntries();
        this.draftComponent.beginEditing();
    }

    private void removeEntry(String entry) {
        this.stringSetSetting.removeEntry(entry);
        this.rebuildEntries();
    }

    private void clearEntries() {
        // Go through removeEntry so the setting's change callback runs, exactly like a row click.
        for (String entry : new ArrayList<>(this.stringSetSetting.getValue())) {
            this.stringSetSetting.removeEntry(entry);
        }
        this.rebuildEntries();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.draftComponent.editing) {
            // The first Escape only leaves the text field; the next one closes the screen.
            this.draftComponent.keyPressed(event.key());
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.draftComponent.editing) {
            this.addDraft();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void closeWithTransition() {
        if (this.parentScreen instanceof ClientScreen parentClientScreen) {
            parentClientScreen.open();
        } else {
            ClientAccess.evernightMinecraft.setScreen(this.parentScreen);
        }
    }
}
