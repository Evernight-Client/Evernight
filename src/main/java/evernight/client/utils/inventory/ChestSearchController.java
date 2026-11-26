package evernight.client.utils.inventory;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.misc.ChestSearchBar.ChestSearchBarModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.settings.options.SearchMode;
import evernight.client.utils.search.SearchKey;
import evernight.client.utils.search.SearchNames;
import evernight.client.utils.search.SearchQuery;
import evernight.mixin.minecraft.gui.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.awt.*;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ChestSearchController {
    public static final ChestSearchController INSTANCE = new ChestSearchController();
    private static final int SLOT_SIZE = 16;
    private static final int DIM_COLOR = 0xAA000000;
    private static final int BACKGROUND_COLOR = 0x4B000000;
    private static final int BORDER_COLOR = 0x71000000;
    private static final int NO_MATCH_TEXT_COLOR = 0xFFFF5555;
    private static final boolean replaymodLoaded;
    public static EditBox searchBox;
    public static String text = "";
    public static int matchedSlotCount = 0;
    private static ChestSearchBarModule chestSearchBarModule = ClientContext.getModule(ChestSearchBarModule.class);
    private static ChestFilter cachedFilter;
    private static String cachedFilterText;
    private static SearchMode cachedFilterMode;

    static {
        replaymodLoaded = FabricLoader.getInstance().isModLoaded("replaymod");
    }

    public static boolean isFull(ItemStack stack, String query) {
        if (!chestSearchBarModule.highlightFullSetting.getValue()) return false;
        ChestFilter filter = filter(query);
        if (filter.isBlank() || stack.isEmpty()) return false;
        if (!stack.getItem().getDescriptionId().contains("shulker_box")) return false;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null || contents.stream().toList().size() != 27) return false;
        for (ItemStack containedStack : contents.stream().toList()) {
            if (!filter.matches(containedStack)) return false;
        }
        return true;
    }

    public static boolean namesMatch(ItemStack stack, String query) {
        ChestFilter filter = filter(query);
        if (filter.isBlank()) return true;
        if (stack.isEmpty()) return false;
        return filter.matches(stack);
    }

    /**
     * Builds (and caches) the filter of one search bar content. The cache is keyed by the raw text and
     * the resolved search mode, so the per-slot overlay loop only parses the query once.
     */
    private static ChestFilter filter(String query) {
        String rawQuery = query == null ? "" : query;
        SearchMode mode = SearchMode.effective();
        if (cachedFilter == null || !Objects.equals(cachedFilterText, rawQuery) || cachedFilterMode != mode) {
            cachedFilterText = rawQuery;
            cachedFilterMode = mode;
            cachedFilter = ChestFilter.of(rawQuery, mode);
        }
        return cachedFilter;
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        chestSearchBarModule = registry.getModule(ChestSearchBarModule.class);
    }

    public void init() {
        searchBox = null;
        text = "";
        ScreenEvents.AFTER_INIT.register(new SearchScreenInitializer());
    }

    public void onDrawGui(GuiGraphics graphics, Screen screen, PoseStack poseStack, float mouseX, float mouseY, float partialTick) {
        if (!chestSearchBarModule.isToggled() || searchBox == null) return;
        poseStack.pushPose();
        graphics.nextStratum();
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        if (!searchBox.getValue().isBlank() && screen instanceof AbstractContainerScreen containerScreen) {
            var menu = containerScreen.getMenu();
            int containerX = accessor.getX();
            int containerY = accessor.getY();
            matchedSlotCount = 0;
            for (Slot slot : menu.slots) {
                int slotX = containerX + slot.x;
                int slotY = containerY + slot.y;
                ItemStack stack = slot.getItem();
                if (!namesMatch(stack, searchBox.getValue())) {
                    graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, DIM_COLOR);
                } else {
                    matchedSlotCount++;
                }
                if (isFull(stack, searchBox.getValue())) {
                    int color = ColorUtils.toArgb(Color.green, 100);
                    graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, color);
                }
            }
        }
        if (matchedSlotCount == 0 && !searchBox.getValue().isEmpty()) {
            searchBox.setTextColor(NO_MATCH_TEXT_COLOR);
        } else {
            searchBox.setTextColor(-1);
        }
        graphics.fill(searchBox.getX() + 2, searchBox.getY(), searchBox.getRight(), searchBox.getBottom(), BACKGROUND_COLOR);
        graphics.hLine(searchBox.getX() + 2, searchBox.getRight(), searchBox.getBottom(), -1);
        graphics.vLine(searchBox.getRight(), searchBox.getY(), searchBox.getBottom(), -1);
        graphics.nextStratum();
        graphics.hLine(searchBox.getX() + 2, searchBox.getRight(), searchBox.getY(), BORDER_COLOR);
        graphics.vLine(searchBox.getX() + 2, searchBox.getY(), searchBox.getBottom() + 1, BORDER_COLOR);
        searchBox.render(graphics, (int) mouseX, (int) mouseY, partialTick);
        poseStack.popPose();
    }

    public static class SearchScreenInitializer implements AfterInit {
        private static final String REPLAYMOD_CONTAINER_PACKAGE = "com.replaymod.lib.de.johni0702.minecraft.gui.container";

        @Override
        public void afterInit(Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight) {
            if (!chestSearchBarModule.isToggled()) {
                searchBox = null;
                return;
            }
            if (replaymodLoaded && screen.getClass().getPackageName().startsWith(REPLAYMOD_CONTAINER_PACKAGE)) return;
            if (!(screen instanceof ContainerScreen) && !(screen instanceof ShulkerBoxScreen) && !(screen instanceof HopperScreen)) {
                searchBox = null;
                return;
            }
            Minecraft client = Minecraft.getInstance();
            AbstractContainerScreen containerScreen = (AbstractContainerScreen) screen;
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
            Font font = client.font;
            int boxX = accessor.getX() + 77;
            int boxY = accessor.getY() + 4;
            searchBox = new EditBox(font, boxX, boxY, 91, 10, Component.literal(""));
            searchBox.setValue(text);
            searchBox.setFocused(false);
            searchBox.setMaxLength(32);
            searchBox.setBordered(true);
            searchBox.setResponder(newText -> text = newText);
            Screens.getButtons(screen).add(searchBox);
        }
    }

    /**
     * Parsed search bar content: a plain query matched through the shared search modes, or the
     * {@code "exact"} / {@code /regex/} forms that keep working on the raw names.
     */
    private static final class ChestFilter {
        private final SearchQuery query;
        private final String exact;
        private final Pattern regex;

        private ChestFilter(SearchQuery query, String exact, Pattern regex) {
            this.query = query;
            this.exact = exact;
            this.regex = regex;
        }

        static ChestFilter of(String rawQuery, SearchMode mode) {
            SearchQuery query = SearchQuery.of(rawQuery, mode);
            String text = query.text();
            if (text.length() >= 3 && text.startsWith("\"") && text.endsWith("\"")) {
                return new ChestFilter(query, text.substring(1, text.length() - 1), null);
            }
            if (text.length() >= 3 && text.startsWith("/") && text.endsWith("/")) {
                try {
                    return new ChestFilter(query, null, Pattern.compile(text.substring(1, text.length() - 1)));
                } catch (PatternSyntaxException ignoredFailure) {
                    // An unfinished expression keeps filtering literally instead of hiding every slot.
                }
            }
            return new ChestFilter(query, null, null);
        }

        boolean isBlank() {
            return this.query.isBlank();
        }

        boolean matches(ItemStack stack) {
            if (matchesContainerContents(stack)) return true;
            if (matchesBundleContents(stack)) return true;
            SearchKey key = SearchNames.forItemStack(stack);
            if (this.exact != null) {
                for (String candidate : key.allTexts()) {
                    if (candidate.toLowerCase(Locale.ROOT).equals(this.exact)) return true;
                }
                return false;
            }
            if (this.regex != null) {
                for (String candidate : key.allTexts()) {
                    if (this.regex.matcher(candidate.toLowerCase(Locale.ROOT)).find()) return true;
                }
                return false;
            }
            return key.matches(this.query);
        }

        private boolean matchesContainerContents(ItemStack stack) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                for (ItemStack containedStack : contents.nonEmptyItems()) {
                    if (this.matches(containedStack)) return true;
                }
            }
            return false;
        }

        private boolean matchesBundleContents(ItemStack stack) {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents != null) {
                for (ItemStack containedStack : contents.itemsCopy()) {
                    if (this.matches(containedStack)) return true;
                }
            }
            return false;
        }
    }
}
