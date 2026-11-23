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
    private static final float TILING_PAN_STEP = 16.0F;
    private static final float TILING_KEY_STEP = 24.0F;
    private static final int TILING_START_X = 10;
    private static final int TILING_START_Y = 5;
    private static final int TILING_GAP = 3;
    /** Share of the remaining distance covered per frame: gives the pan a damped follow-up. */
    private static final float PAN_DAMPING = 0.30F;
    private static final float SCALE_DAMPING = 0.25F;
    private static final float MIN_SCALE = 0.1F;
    private static final float MAX_SCALE = 2.0F;
    private static final float SCALE_STEP = 0.05F;
    private float panX;
    private float panY;
    private float panTargetX;
    private float panTargetY;
    private float scaleTarget = 1.2F;
    private boolean tilingLayoutActive;

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

    @Override
    protected boolean isPanelDraggingAllowed() {
        return !UIModule.INSTANCE.tilingSetting.getValue();
    }

    /**
     * Lays the category panels out as one horizontal strip shifted by the current pan offset.
     *
     * <p>Called before the panels are drawn and after every pan change; without tiling it does nothing
     * and the panels keep their dragged positions.
     */
    public void applyTilingLayout() {
        boolean tilingEnabled = UIModule.INSTANCE.tilingSetting.getValue();
        if (tilingEnabled && !this.tilingLayoutActive) {
            // Entering the layout starts from the first panel instead of whatever pan was left over.
            this.panX = 0.0F;
            this.panY = 0.0F;
            this.panTargetX = 0.0F;
            this.panTargetY = 0.0F;
        }
        this.tilingLayoutActive = tilingEnabled;
        if (!tilingEnabled) {
            return;
        }
        int cursorX = TILING_START_X;
        for (Panel panel : this.panels) {
            panel.setX(Math.round(cursorX - this.panX));
            panel.setY(Math.round(TILING_START_Y - this.panY));
            cursorX += panel.getWidth() + TILING_GAP;
        }
    }

    /**
     * Arrow keys pan the strip as well, so the layout can be driven without the wheel. Ignored while a
     * text field owns the keyboard, otherwise the search box and editing settings would eat the arrows.
     */
    private boolean handleTilingKey(int keyCode) {
        if (!UIModule.INSTANCE.tilingSetting.getValue()) {
            return false;
        }
        if (this.searchBox.isFocused() || this.getEditingSetting() != null) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> this.panTargetY -= TILING_KEY_STEP;
            case GLFW.GLFW_KEY_DOWN -> this.panTargetY += TILING_KEY_STEP;
            case GLFW.GLFW_KEY_LEFT -> this.panTargetX -= TILING_KEY_STEP;
            case GLFW.GLFW_KEY_RIGHT -> this.panTargetX += TILING_KEY_STEP;
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean isMouseOverAnyPanel(double mouseX, double mouseY) {
        for (Panel panel : this.panels) {
            if (panel.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Damped follow-up of the pan and zoom targets. Called once per frame so wheel and arrow input feel
     * continuous instead of jumping, and the strip keeps gliding for a few frames after the input stops.
     */
    private void updateTilingAnimation() {
        this.panX += (this.panTargetX - this.panX) * PAN_DAMPING;
        this.panY += (this.panTargetY - this.panY) * PAN_DAMPING;
        if (Math.abs(this.panTargetX - this.panX) < 0.05F) {
            this.panX = this.panTargetX;
        }
        if (Math.abs(this.panTargetY - this.panY) < 0.05F) {
            this.panY = this.panTargetY;
        }
        float nextScale = this.guiScale + (this.scaleTarget - this.guiScale) * SCALE_DAMPING;
        if (Math.abs(this.scaleTarget - nextScale) < 0.0005F) {
            nextScale = this.scaleTarget;
        }
        this.guiScale = nextScale;
    }

    /**
     * Restores the generated layout: neutral pan and zoom, panels back to their default row and every
     * expanded module collapsed.
     */
    public void resetGuiLayout() {
        this.panX = 0.0F;
        this.panY = 0.0F;
        this.panTargetX = 0.0F;
        this.panTargetY = 0.0F;
        float defaultScale = UIModule.INSTANCE.scaleSetting.getDefaultValue();
        this.scaleTarget = defaultScale;
        this.guiScale = defaultScale;
        UIModule.INSTANCE.scaleSetting.setValue(defaultScale);
        int panelX = TILING_START_X;
        for (Panel panel : this.panels) {
            panel.setX(panelX);
            panel.setY(TILING_START_Y);
            panelX += panel.getWidth() + TILING_GAP;
        }
        for (Object openComponent : this.openModuleComponents) {
            ((ModuleComponent) openComponent).setOpen(false);
        }
        this.openModuleComponents.clear();
        this.applyTilingLayout();
    }

    /**
     * Wheel pans the strip vertically, Shift + wheel pans it horizontally and Ctrl + wheel resizes the
     * whole GUI. Only active while the tiling layout is enabled; otherwise the panels scroll as usual.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0 && isControlDown()) {
            this.zoomGui(verticalAmount > 0.0 ? SCALE_STEP : -SCALE_STEP);
            return true;
        }
        if (UIModule.INSTANCE.tilingSetting.getValue() && verticalAmount != 0.0) {
            double scale = GuiScaleUtils.calculateScaleMultiplier(this.guiScale);
            if (this.isMouseOverAnyPanel(mouseX / scale, mouseY / scale)) {
                // Keep the panels' own content scrolling usable: the wheel scrolls the panel under the
                // cursor, and only the empty canvas pans the strip.
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            float step = (float) (-verticalAmount * TILING_PAN_STEP);
            if (isShiftDown()) {
                this.panTargetX += step;
            } else {
                this.panTargetY += step;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Ctrl + wheel zoom: the scale setting is adjusted in small steps and applied to the open screen.
     */
    /**
     * Ctrl + wheel zoom: the target is limited to the supported range, the persisted setting follows the
     * target and the screen eases towards it in {@link #updateTilingAnimation()}.
     */
    private void zoomGui(float delta) {
        float nextScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, this.scaleTarget + delta));
        if (nextScale == this.scaleTarget) {
            return;
        }
        this.scaleTarget = nextScale;
        UIModule.INSTANCE.scaleSetting.setValue(nextScale);
    }

    private static boolean isShiftDown() {
        long window = ClientAccess.evernightMinecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
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
        this.updateTilingAnimation();
        this.applyTilingLayout();
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
        this.scaleTarget = this.guiScale;
        this.applyTilingLayout();
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
            if (event.key() == GLFW.GLFW_KEY_F1 && isShiftDown()) {
                // Shift + F1: put the ClickGUI back to its generated layout.
                this.resetGuiLayout();
                return true;
            }
            if (this.handleTilingKey(event.key())) {
                return true;
            }
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