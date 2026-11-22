package evernight.client.gui.screens.clickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.config.preset.ModulePresetCodec;
import evernight.client.gui.core.GuiElement;
import evernight.client.gui.components.ModuleComponent;
import evernight.client.gui.components.setting.SettingComponent;
import evernight.client.gui.panels.ModuleCategoryPanel;
import evernight.client.gui.panels.Panel;
import evernight.client.modules.HudWidgetModule;
import evernight.client.modules.Keybind;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.render.*;
import evernight.client.render.text.Translations;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.FloatAnimation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import evernight.client.gui.screens.core.ClientScreen;
import evernight.client.gui.screens.preset.ModuleImportConfirmationScreen;
import evernight.client.gui.screens.preset.PresetImportScreen;
import evernight.client.render.color.Catppuccin;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.font.TextUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.gui.GuiScaleUtils;

public class ClickGuiScreen extends ClientScreen implements ClientAccess {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final HUDModule HUD = ClientContext.getModule(HUDModule.class);
    private final FloatAnimation searchFocusAnimation = new FloatAnimation();
    private final ElapsedTimer searchCursorTimer = new ElapsedTimer();
    public EditBox searchBox;
    public List openModuleComponents = new ArrayList();
    private SettingComponent<?> editingSetting = null;
    private boolean searchCursorVisible;
    private boolean keepSearchOnNextReset;
    private String searchCompletion;

    public ClickGuiScreen() {
        int panelX = 10;
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category != ModuleCategory.HUD) {
                ModuleCategoryPanel categoryPanel = new ModuleCategoryPanel(category);
                categoryPanel.setX(panelX);
                this.panels.add(categoryPanel);
                panelX += categoryPanel.getWidth() + 3;
            }
        }
    }

    private static boolean isControlDown() {
        long window = ClientAccess.evernightMinecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void handleSearchKey(int keyCode) {
        String searchText = this.searchBox.getValue();
        switch (keyCode) {
            case GLFW.GLFW_KEY_F:
                if (isControlDown()) {
                    if (this.searchBox.isFocused()) {
                        this.reset();
                    } else {
                        this.setInitialFocus(this.searchBox);
                    }
                }
                break;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                boolean foundModule = false;
                for (Panel panel : this.panels) {
                    for (Object childEntry : panel.getChildren()) {
                        GuiElement element = (GuiElement) childEntry;
                        if (element instanceof ModuleComponent moduleComponent) {
                            Module searchedModule = moduleComponent.getModule();
                            String moduleName = searchedModule.getName();
                            String completedSearch = TextUtils.normalizeKeyboardLayout(
                                    searchText + this.searchCompletion);
                            if (moduleName.equalsIgnoreCase(completedSearch)
                                    || searchedModule.getDisplayName().equalsIgnoreCase(completedSearch)) {
                                moduleComponent.setOpen(true);
                                moduleComponent.startSearchHighlight();
                                foundModule = true;
                                break;
                            }
                        }
                    }
                    if (foundModule) {
                        break;
                    }
                }
                this.reset();
                break;
            case GLFW.GLFW_KEY_TAB:
                if (this.searchBox.isFocused()) {
                    EditBox completionBox = this.searchBox;
                    String currentCompletion = this.searchCompletion;
                    completionBox.setValue(TextUtils.normalizeKeyboardLayout(searchText + currentCompletion));
                }
                break;
            case GLFW.GLFW_KEY_BACKSPACE:
            case GLFW.GLFW_KEY_DELETE:
                if (this.searchBox.isFocused()
                        && !searchText.isEmpty()) {
                    this.searchBox.setValue(isControlDown()
                            ? ""
                            : searchText.substring(0, searchText.length() - 1));
                }
        }
    }

    private String getSearchCursor() {
        if (this.searchCursorTimer.hasElapsed(500L)) {
            this.searchCursorVisible = !this.searchCursorVisible;
            this.searchCursorTimer.reset();
        }
        return this.searchCursorVisible ? "_" : "";
    }

    private void handlePresetPaste(int keyCode) {
        if (isControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            ModulePresetCodec presetCodec = ClientContext.configurationManager.getModulePresetCodec();
            try {
                ModulePresetCodec.DecodedPreset decodedPreset = presetCodec.decode(
                        ClientAccess.evernightMinecraft.keyboardHandler.getClipboard());
                ModuleImportConfirmationScreen presetScreen = new ModuleImportConfirmationScreen(
                        ClientContext.getClickGui(), decodedPreset.module(), decodedPreset.json());
                presetScreen.open();
            } catch (Exception pasteException) {
                LOGGER.warn("Could not load preset from clipboard", pasteException);
            }
        }
    }

    public EditBox getSearchBox() {
        return this.searchBox;
    }

    public SettingComponent<?> getEditingSetting() {
        return this.editingSetting;
    }

    public void setEditingSetting(SettingComponent<?> component) {
        this.editingSetting = component;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale);
        this.keepSearchOnNextReset = this.searchBox.isMouseOver(event.x() / scale, event.y() / scale);
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            this.keepSearchOnNextReset = false;
        }
    }

    @Override
    protected void reset() {
        super.reset();
        if (this.keepSearchOnNextReset) {
            return;
        }
        this.setFocused(null);
        this.searchBox.setFocused(false);
        this.searchBox.setValue("");
        this.searchBox.setSuggestion("");
    }

    @Override
    public void onFilesDrop(List paths) {
        PresetImportScreen.openForDroppedFiles(this, paths);
    }

    @Override
    public void renderContents(
            GuiGraphics graphics, PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.searchFocusAnimation.setTarget(this.searchBox.isFocused(), 300L);
        this.drawSearchDimmer(poseStack);
        int fontHeight = FontRenderer.INSTANCE.getHeight();
        if (this.openModuleComponents.size() >= (3)) {
            ((ModuleComponent) this.openModuleComponents.getFirst()).setOpen(false);
        }
        this.openModuleComponents.removeIf((java.util.function.Predicate<evernight.client.gui.components.ModuleComponent>) ModuleComponent::isClosed);
        if (this.searchBox.isFocused()) {
            FontRenderer fontRenderer = FontRenderer.INSTANCE;
            String searchText = this.searchBox.getValue();
            String completionText = this.searchCompletion;
            float searchTextWidth = fontRenderer.getWidth(searchText + completionText + "_");
            completionText = this.searchBox.getValue();
            String completionFormatting = String.valueOf(ChatFormatting.GRAY);
            String completionSuffix = this.searchCompletion;
            String cursor = this.getSearchCursor();
            String cursorFormatting = String.valueOf(ChatFormatting.WHITE);
            String searchLabel = completionText + completionFormatting + completionSuffix
                    + cursorFormatting + cursor;
            fontRenderer.drawWithShadow(poseStack, searchLabel, GuiScaleUtils.getScaledWidth() / 2.0F
                    - searchTextWidth / 2.0F, GuiScaleUtils.getScaledHeight() / 2.0F - fontHeight
                    / 2.0F, Catppuccin.TEXT);
        } else {
            String searchHint = Translations.ui("gui.Search");
            float hintY = GuiScaleUtils.getScaledHeight() - fontHeight - 1;
            if (this.getParentScreen() instanceof TitleScreen) {
                hintY -= fontHeight + 1;
            }
            Color hintColor = ColorUtils.pulse(Catppuccin.OVERLAY1, Catppuccin.TEXT, 2000.0D, 250.0D);
            int hudMargin = HUD.getSafeX();
            FontRenderer.INSTANCE.drawWithShadow(poseStack, searchHint, GuiScaleUtils.getScaledWidth()
                    - FontRenderer.INSTANCE.getWidth(searchHint) - hudMargin, hintY - 1.0F, hintColor);
        }
    }

    private void drawSearchDimmer(PoseStack poseStack) {
        float dimmerOpacity = this.searchFocusAnimation.getValue() * 0.2F;
        int dimmerColor = ARGB.color(dimmerOpacity, Catppuccin.CRUST.getRGB());
        GuiDrawUtils.fillRect(poseStack, 0.0F, 0.0F,
                ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth(),
                ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight(), dimmerColor);
    }

    @Override
    public void init() {
        super.init();
        this.searchCompletion = "";
        this.searchBox = new EditBox(ClientAccess.evernightMinecraft.font, 7, -100, 100, 9,
                Component.literal(Translations.ui("gui.Search"))
        );
        this.searchBox.setVisible(true);
        this.searchBox.setEditable(true);
        this.searchBox.setMaxLength(64);
        this.searchBox.setResponder(searchText -> {
                    if (!searchText.isEmpty()) {
                        for (Object moduleEntry : ClientContext.moduleRegistry.getRegistry()) {
                            Module module = (Module) moduleEntry;
                            if (!(module instanceof HUDModule) && !(module instanceof HudWidgetModule)
                                    && module.getName().toLowerCase(Locale.ROOT).startsWith(TextUtils.normalizeKeyboardLayout(searchText.toLowerCase(Locale.ROOT)))) {
                                this.searchCompletion = module.getName()
                                        .substring(this.searchBox.getCursorPosition());
                                return;
                            }
                        }
                    }
                    this.searchCompletion = "";
                }
        );
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        super.keyPressed(event);
        Keybind toggleKeybind = UIModule.INSTANCE.getKeybind();
        if (toggleKeybind.code() == event.key() && !toggleKeybind.mouse() && this.escapeCloseAllowed
                && !this.searchBox.isFocused()) {
            // The binding is the vanilla KeyMapping, so the same key that opened the GUI closes it.
            this.closeWithTransition();
            return true;
        } else {
            this.handleSearchKey(event.key());
            this.handlePresetPaste(event.key());
            return true;
        }
    }

    @Override
    public void closeWithTransition() {
        super.closeWithTransition();
        ClientAccess.executorService.submit(() -> ClientContext.configurationManager.save());
    }
}