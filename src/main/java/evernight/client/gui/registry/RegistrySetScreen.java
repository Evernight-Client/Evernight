package evernight.client.gui.registry;

import evernight.client.ClientAccess;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.setting.StringSettingComponent;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.core.KeyUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public class RegistrySetScreen<E> extends ClientScreen {
    protected final RegistrySetSetting<E> registrySetting;
    private final Panel availablePanel = new RegistryListPanel(
            "Available"
    );
    private final Panel addedPanel = new RegistryListPanel(
            "Added"
    );
    private final Panel extraPanel = new RegistryListPanel(
            "Extra"
    );
    protected final StringSettingComponent searchComponent = new StringSettingComponent(this.extraPanel,
            CloseState.ALWAYS_OPEN, this.createSearchSetting());
    private final RegistryEntryCatalog.EntryList catalogEntries;
    private final Screen parentScreen;

    public RegistrySetScreen(Screen parentScreen, RegistrySetSetting<E> registrySetting) {
        this.registrySetting = registrySetting;
        this.parentScreen = parentScreen;
        this.catalogEntries = RegistryEntryCatalog.entriesFor(registrySetting.getRegistry());
        for (E entry : registrySetting.getRegistry()) {
            ResourceKey entryKey = registrySetting.getRegistry().getResourceKey(entry)
                    .orElse(null);
            if (entryKey != null && !this.catalogEntries.contains(entry)) {
                this.addEntryPair(entryKey.identifier(), entry, false);
            }
        }
        for (Object catalogEntry : this.catalogEntries.orderedEntries()) {
            E entry = registrySetting.getRegistry().stream().filter(candidate -> candidate == catalogEntry).findFirst().orElse(null);
            ResourceKey catalogEntryKey = entry == null ? null : registrySetting.getRegistry().getResourceKey(entry).orElse(null);
            if (catalogEntryKey != null) {
                this.addEntryPair(catalogEntryKey.identifier(), entry, true);
            }
        }
        this.initializePanels();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (KeyUtils.isControlDown() && event.key() == 70) {
            this.searchComponent.beginEditing();
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

    private void addEntryPair(Identifier identifier, E entry, boolean prepend) {
        RegistryEntryComponent<E> availableEntry = new RegistryEntryComponent<>(
                this, this.availablePanel, identifier, entry, true);
        RegistryEntryComponent<E> addedEntry = new RegistryEntryComponent<>(
                this, this.addedPanel, identifier, entry, false);
        if (prepend) {
            this.availablePanel.getRegistry().addFirst(availableEntry);
            this.addedPanel.getRegistry().addFirst(addedEntry);
        } else {
            this.availablePanel.registerChild(availableEntry);
            this.addedPanel.registerChild(addedEntry);
        }
    }

    private void initializePanels() {
        this.addedPanel.setX(this.availablePanel.getX() + this.availablePanel.getWidth() + (3));
        this.extraPanel.setX(this.addedPanel.getX() + this.addedPanel.getWidth() + (3));
        this.getPanels().add(this.availablePanel);
        this.getPanels().add(this.addedPanel);
        this.getPanels().add(this.extraPanel);
        this.extraPanel.registerChild(this.searchComponent);
        this.extraPanel.registerChild(new ActionButtonComponent(this.extraPanel,
                "gui.Clear",
                () -> this.registrySetting.getValue().clear()));
    }

    private StringSetting createSearchSetting() {
        StringSetting searchSetting = new StringSetting("Search", "");
        searchSetting.setChangeCallback(() -> {
            if (!searchSetting.getValue().isEmpty()) {
                searchSetting.parseValue("");
            }
        });
        return searchSetting;
    }
}