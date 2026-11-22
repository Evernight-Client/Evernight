package evernight.client.modules.misc.ChestSearchBar;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.ui.AbstractContainerScreenEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.RenderUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.KeyUtils;
import evernight.client.utils.inventory.ChestSearchController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public class ChestSearchBarModule extends Module {
    public final Setting<Boolean> highlightFullSetting;
    private final Setting<Boolean> chestStealerSetting;
    private final Setting<Boolean> onlyFullSetting;

    public ChestSearchBarModule() {
        super("ChestSearchBar", ModuleCategory.MISC);
        Boolean highlightFullDefault = false;
        this.highlightFullSetting = this.add(new BooleanSetting(("HighlightFull"), highlightFullDefault));
        Boolean chestStealerDefault = false;
        BooleanSetting chestStealerOption = new BooleanSetting(("ChestStealer"), chestStealerDefault);
        chestStealerOption.markVisibilityDependency();
        this.chestStealerSetting = this.add(chestStealerOption);
        Boolean onlyFullDefault = false;
        BooleanSetting onlyFullOption = new BooleanSetting(("OnlyFull"), onlyFullDefault);
        this.onlyFullSetting = this.add(onlyFullOption.dependsOn(this.chestStealerSetting));
        ChestSearchController searchController = ChestSearchController.INSTANCE;
        searchController.init();
        this.setDrawn(false);
    }

    public boolean match(ItemStack stack, boolean requireFull) {
        if (ChestSearchController.searchBox == null) {
            return true;
        }
        EditBox searchBox = ChestSearchController.searchBox;
        String query = searchBox.getValue();
        if (query.isEmpty()) {
            return true;
        }
        if (requireFull && !ChestSearchController.isFull(stack, query)) {
            return false;
        }
        return ChestSearchController.namesMatch(stack, query);
    }

    public boolean shouldSkip(ItemStack stack) {
        if (!this.isToggled()) {
            return false;
        }
        Setting<Boolean> chestStealerSettingSnapshot = this.chestStealerSetting;
        Boolean filterChestStealer = chestStealerSettingSnapshot.getValue();
        if (!filterChestStealer) {
            return false;
        }
        Setting<Boolean> onlyFullSettingSnapshot = this.onlyFullSetting;
        boolean requireFullSnapshot = onlyFullSettingSnapshot.getValue();
        return !this.match(stack, requireFullSnapshot);
    }

    @EventHandler
    private void onRender(AbstractContainerScreenEvent event) {
        ChestSearchController searchControllerSnapshot = ChestSearchController.INSTANCE;
        GuiGraphics graphicsSnapshot = event.getGraphics();
        Screen screenSnapshot = event.getScreen();
        PoseStack poseSnapshot = event.getPoseStack();
        float mouseXSnapshot = event.getMouseX();
        float mouseYSnapshot = event.getMouseY();
        float renderValueSnapshot = RenderUtils.getPartialTick();
        searchControllerSnapshot.onDrawGui(graphicsSnapshot, screenSnapshot, poseSnapshot, mouseXSnapshot, mouseYSnapshot, renderValueSnapshot);
        if (ChestSearchController.searchBox != null) {
            if (KeyUtils.isPhysicalKeyDown(70)) {
                if (KeyUtils.isControlDown()) {
                    Screen focusScreen = ClientAccess.evernightMinecraft.screen;
                    EditBox focusTarget = ChestSearchController.searchBox;
                    focusScreen.setFocused(focusTarget);
                }
            }
            if (KeyUtils.isPhysicalKeyDown(257)) {
                Screen focusScreen = ClientAccess.evernightMinecraft.screen;
                focusScreen.setFocused(null);
            }
        }
    }
}