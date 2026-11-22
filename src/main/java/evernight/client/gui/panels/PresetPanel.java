package evernight.client.gui.panels;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.config.preset.PresetData;
import evernight.client.config.preset.PresetUndoAction;
import evernight.client.gui.core.CloseState;
import evernight.client.gui.panels.Panel;
import evernight.client.gui.screens.preset.PresetScreen;
import evernight.client.managers.registry.PresetRegistry;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.StringSetting;
import evernight.client.settings.options.PresetCategory;
import net.minecraft.util.Util;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import evernight.client.gui.components.base.ActionButtonComponent;
import evernight.client.gui.components.base.ButtonComponent;
import evernight.client.gui.components.setting.EnumSettingComponent;
import evernight.client.gui.components.setting.StringSettingComponent;

public class PresetPanel extends Panel {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final StringSetting saveNameSetting = new StringSetting("Save", "");
    private final EnumSetting<PresetCategory> categorySetting = new EnumSetting<>(
            "Category", PresetCategory.MODULES
    );
    private final PresetScreen screen;
    private PresetPanelMode mode = PresetPanelMode.NEW;
    private PresetData selectedPreset;
    private boolean listRefreshPending;
    private boolean controlsRebuildPending;

    public PresetPanel(PresetScreen screen) {
        super("Preset");
        this.screen = screen;
        this.saveNameSetting.setChangeCallback(() -> {
                    try {
                        PresetUndoAction action = PresetUndoAction.beforeSave(
                                this.getSelectedCategoryPresets(), this.saveNameSetting.getValue());
                        this.getSelectedCategoryPresets().savePreset(this.saveNameSetting.getValue());
                        ClientContext.configurationManager.getPresetUndoState().push(action);
                        this.requestListRefresh();
                    } catch (IOException saveFailure) {
                        LOGGER.warn("Could not save preset {}", this.saveNameSetting.getValue(), saveFailure);
                    }
                    this.saveNameSetting
                            .setValue("");
                }
        );
        this.categorySetting.setChangeCallback(() -> {
                    screen.setSelectedCategory(this.categorySetting.getValue());
                    this.requestListRefresh();
                }
        );
    }

    private static String[] createExplorerCommand(Path path) {
        String[] command = new String[3];
        command[0] = "explorer.exe";
        command[1] = "/select,";
        command[2] = path.toString();
        return command;
    }

    @Override
    public void init() {
        this.rebuildControls();
    }

    @Override
    public void render(PoseStack poses, double mouseX, double mouseY) {
        if (this.controlsRebuildPending) {
            this.rebuildControls();
            this.controlsRebuildPending = false;
        }
        if (this.listRefreshPending) {
            this.screen.getListPanel().refreshPresets();
            this.listRefreshPending = false;
        }
        super.render(poses, mouseX, mouseY);
    }

    public void rebuildControls() {
        this.children.clear();
        switch (this.mode) {
            case NEW:
                this.addChild(new EnumSettingComponent<>(this, CloseState.ALWAYS_OPEN, this.categorySetting));
                this.addChild(new StringSettingComponent(this, CloseState.ALWAYS_OPEN, this.saveNameSetting));
                this.addChild(
                        new ActionButtonComponent(this, "gui.Refresh", this::requestListRefresh)
                );
                this.addChild(new ActionButtonComponent(this,
                        "gui.Undo",
                        () -> {
                            try {
                                ClientContext.configurationManager.getPresetUndoState().undo();
                                this.requestListRefresh();
                            } catch (IOException undoFailure) {
                                LOGGER.warn("Could not undo preset change", undoFailure);
                            }
                        }
                ));
                break;
            case EDIT:
                this.addChild(new ButtonComponent(this,
                        () -> Translations.ui("hud.PresetLabel") + this.selectedPreset.getName(), false
                ));
                this.addChild(new ActionButtonComponent(this,
                        "gui.Load",
                        () -> {
                            PresetUndoAction action = PresetUndoAction.beforeLoad(this.selectedPreset.getName());
                            if (this.getSelectedCategoryPresets().applyPreset(this.selectedPreset.getName())) {
                                ClientContext.configurationManager.getPresetUndoState().push(action);
                            }
                        }
                ));
                this.addChild(new ActionButtonComponent(this,
                        "gui.Save",
                        () -> {
                            try {
                                PresetUndoAction action = PresetUndoAction.beforeSave(
                                        this.getSelectedCategoryPresets(), this.selectedPreset.getName());
                                this.getSelectedCategoryPresets().savePreset(this.selectedPreset.getName());
                                ClientContext.configurationManager.getPresetUndoState().push(action);
                                this.requestListRefresh();
                            } catch (IOException ignoredSaveFailure) {
                            }
                        }
                ));
                this.addChild(new ActionButtonComponent(this,
                        "gui.Delete",
                        () -> {
                            PresetUndoAction action = PresetUndoAction.beforeDelete(
                                    this.getSelectedCategoryPresets(), this.selectedPreset.getName());
                            if (this.getSelectedCategoryPresets().deletePreset(this.selectedPreset.getName())) {
                                ClientContext.configurationManager.getPresetUndoState().push(action);
                            }
                            this.setMode(PresetPanelMode.NEW);
                        }
                ));
                this.addChild(
                        new ActionButtonComponent(this, "gui.Open", this::openPresetLocation)
                );
                this.addChild(new ActionButtonComponent(this,
                        "gui.Back",
                        () -> this.setMode(PresetPanelMode.NEW)));
        }
        this.requestListRefresh();
    }

    private void requestListRefresh() {
        this.listRefreshPending = true;
    }

    private PresetRegistry getSelectedCategoryPresets() {
        return ClientContext.configurationManager.getPresetRegistry(this.screen.getSelectedCategory());
    }

    public void editPreset(PresetData preset) {
        this.selectedPreset = preset;
        this.setMode(PresetPanelMode.EDIT);
    }

    public PresetData getSelectedPreset() {
        return this.mode != PresetPanelMode.EDIT ? null : this.selectedPreset;
    }

    public void setMode(PresetPanelMode mode) {
        this.mode = mode;
        this.controlsRebuildPending = true;
    }

    private void openPresetLocation() {
        try {
            if (!SystemUtils.IS_OS_WINDOWS_10 && !SystemUtils.OS_NAME.startsWith("Windows 11")) {
                Util.getPlatform().openPath(this.screen.getSelectedCategory().getDirectory());
            } else {
                Path path = this.screen.getSelectedCategory().getDirectory().toAbsolutePath();
                PresetData preset = this.selectedPreset;
                path = path.resolve(
                        preset.getName() + ".json"
                );
                new ProcessBuilder(createExplorerCommand(path)).start();
            }
        } catch (Throwable ignoredOpenFailure) {
        }
    }
}