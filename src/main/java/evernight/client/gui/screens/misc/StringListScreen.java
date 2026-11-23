package evernight.client.gui.screens.misc;

import evernight.client.ClientAccess;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.setting.StringSettingComponent;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.settings.types.StringListSetting;
import evernight.client.settings.types.StringSetting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * In-game editor for a {@link StringListSetting}.
 *
 * <p>The left panel lists the entries in order; clicking one loads it into the text field on the
 * right. The right panel holds the text field plus Add / Save / Delete / Up / Down / Clear, so a
 * message list can be created, rewritten, reordered and emptied without a command or an external
 * file. Enter inside the text field applies the current field: it appends when no row is selected
 * and updates the selected row otherwise.
 */
public final class StringListScreen extends ClientScreen {
    private final StringListSetting stringListSetting;
    private final Screen parentScreen;
    private final Panel entriesPanel = new Panel("List");
    private final Panel actionPanel = new Panel("Extra");
    private final StringSetting draftSetting = new StringSetting("Message", "");
    private final StringSettingComponent draftComponent;
    private int selectedIndex = -1;

    public StringListScreen(Screen parentScreen, StringListSetting stringListSetting) {
        this.stringListSetting = stringListSetting;
        this.parentScreen = parentScreen;
        this.draftComponent = new StringSettingComponent(this.actionPanel, CloseState.ALWAYS_OPEN,
                this.draftSetting);
        this.actionPanel.registerChild(this.draftComponent);
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Add", this::addDraft));
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Save", this::saveDraft));
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Delete", this::deleteSelected));
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Up",
                () -> this.moveSelected(-1)));
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Down",
                () -> this.moveSelected(1)));
        this.actionPanel.registerChild(new ActionButtonComponent(this.actionPanel, "gui.Clear", this::clearEntries));
        this.actionPanel.setX(this.entriesPanel.getX() + this.entriesPanel.getWidth() + 4);
        this.panels.add(this.entriesPanel);
        this.panels.add(this.actionPanel);
        this.rebuildEntries();
    }

    /**
     * Recreates one row per entry so edits, removals and reordering show up immediately.
     */
    private void rebuildEntries() {
        List<String> entries = this.stringListSetting.getValue();
        if (this.selectedIndex >= entries.size()) {
            this.selectedIndex = entries.size() - 1;
        }
        this.entriesPanel.getRegistry().clear();
        for (int index = 0; index < entries.size(); index++) {
            int entryIndex = index;
            String label = (index + 1) + ". " + entries.get(index);
            this.entriesPanel.registerChild(new ActionButtonComponent(this.entriesPanel, () -> label,
                    () -> this.selectEntry(entryIndex), false));
        }
    }

    private void selectEntry(int index) {
        List<String> entries = this.stringListSetting.getValue();
        if (index < 0 || index >= entries.size()) {
            return;
        }
        this.selectedIndex = index;
        this.draftComponent.setText(entries.get(index));
    }

    private void addDraft() {
        String text = this.draftComponent.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        this.stringListSetting.addEntry(text);
        this.selectedIndex = -1;
        this.rebuildEntries();
        this.resetDraft();
    }

    private void saveDraft() {
        String text = this.draftComponent.getText();
        if (text == null || text.isBlank() || this.selectedIndex < 0
                || this.selectedIndex >= this.stringListSetting.getValue().size()) {
            return;
        }
        this.stringListSetting.setEntry(this.selectedIndex, text);
        this.rebuildEntries();
    }

    private void deleteSelected() {
        if (this.selectedIndex < 0) {
            return;
        }
        this.stringListSetting.removeEntry(this.selectedIndex);
        this.selectedIndex = -1;
        this.rebuildEntries();
        this.resetDraft();
    }

    private void moveSelected(int offset) {
        int targetIndex = this.selectedIndex + offset;
        List<String> entries = this.stringListSetting.getValue();
        if (this.selectedIndex < 0 || targetIndex < 0 || targetIndex >= entries.size()) {
            return;
        }
        this.stringListSetting.moveEntry(this.selectedIndex, targetIndex);
        this.selectedIndex = targetIndex;
        this.rebuildEntries();
    }

    private void clearEntries() {
        this.stringListSetting.clearEntries();
        this.selectedIndex = -1;
        this.rebuildEntries();
        this.resetDraft();
    }

    private void resetDraft() {
        // The text field only exposes the typed buffer while editing; leave edit mode to drop it,
        // then re-enter it so the player can type the next entry straight away.
        this.draftSetting.setValue("");
        this.draftComponent.editing = false;
        this.draftComponent.beginEditing();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.draftComponent.editing) {
            // The first Escape only leaves the text field; the next one closes the screen.
            this.draftComponent.keyPressed(event.key());
            return false;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && this.draftComponent.editing) {
            if (this.selectedIndex >= 0) {
                this.saveDraft();
            } else {
                this.addDraft();
            }
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
