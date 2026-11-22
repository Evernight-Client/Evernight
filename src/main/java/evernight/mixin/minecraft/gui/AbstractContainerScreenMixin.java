package evernight.mixin.minecraft.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.access.TooltipComponentCapture;
import evernight.client.events.ui.AbstractContainerScreenEvent;
import evernight.client.events.ui.AbstractContainerScreenRenderTooltipEvent;
import evernight.client.modules.misc.ChestSearchBar.ChestSearchBarModule;
import evernight.client.modules.player.InventoryTweaks.InventoryTweaksModule;
import evernight.client.modules.render.Tooltips.TooltipsModule;
import evernight.client.utils.core.KeyUtils;
import evernight.client.utils.inventory.ChestSearchController;
import evernight.client.utils.inventory.ContainerTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;
import java.util.Optional;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin implements ClientAccess {
    private static final ChestSearchBarModule evernight$chestSearchBar = ClientContext.getModule(ChestSearchBarModule.class);
    private static final TooltipsModule evernight$tooltips = ClientContext.getModule(TooltipsModule.class);
    private static final InventoryTweaksModule evernight$inventoryTweaks = ClientContext.getModule(InventoryTweaksModule.class);
    @Unique
    private final AbstractContainerScreenEvent evernight$containerScreenEvent = new AbstractContainerScreenEvent(
            (AbstractContainerScreen) (Object) this);
    @Shadow
    @Nullable
    protected Slot hoveredSlot;
    @Unique
    private int evernight$mouseX;
    @Unique
    private int evernight$mouseY;
    @Unique
    private int evernight$tooltipX;
    @Unique
    private int evernight$tooltipY;
    @Unique
    private Slot evernight$cachedHoveredSlot;
    @Unique
    private MapItemSavedData evernight$slotMapData;
    @Unique
    private int evernight$slotMapId;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    public void evernight$preventInventoryCloseDuringSearch(
            KeyEvent keyEvent, CallbackInfoReturnable<Boolean> callback) {
        if (evernight$chestSearchBar != null && evernight$chestSearchBar.isToggled()
                && ChestSearchController.searchBox != null && ChestSearchController.searchBox.isFocused()
                && ClientAccess.evernightMinecraft.options.keyInventory.matches(keyEvent)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void evernight$runContainerTickCallback(CallbackInfo callback) {
        this.evernight$containerScreenEvent.runTickCallback();
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void evernight$postContainerScreenEvent(
            GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
        this.evernight$containerScreenEvent.setGraphics(graphics);
        this.evernight$containerScreenEvent.setMouseX(mouseX);
        this.evernight$containerScreenEvent.setMouseY(mouseY);
        ClientAccess.eventBus.post(this.evernight$containerScreenEvent);
    }

    @Inject(method = "renderContents", at = @At("HEAD"))
    private void evernight$captureHoveredSlotAndMouse(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback) {
        if (!KeyUtils.isPhysicalKeyDown(342)) {
            this.evernight$cachedHoveredSlot = this.hoveredSlot;
        }
        this.evernight$mouseX = mouseX;
        this.evernight$mouseY = mouseY;
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void evernight$useInspectedHoveredSlot(
            double mouseX, double mouseY, CallbackInfoReturnable<Slot> callback) {
        if (this.evernight$cachedHoveredSlot != null && evernight$tooltips.shouldInspectContents(
                this.evernight$cachedHoveredSlot.getItem())) {
            callback.setReturnValue(this.evernight$cachedHoveredSlot);
        }
    }

    @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
    public boolean evernight$keepInspectedClickInside(boolean originalInside) {
        return (this.evernight$cachedHoveredSlot == null
                || !evernight$tooltips.shouldInspectContents(this.evernight$cachedHoveredSlot.getItem()))
                && originalInside;
    }

    @ModifyArgs(
            method = "mouseClicked",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", ordinal = 1)
    )
    private void evernight$useBundleInspectButton(Args arguments) {
        int mouseButton = arguments.<Integer>get(2);
        if (this.evernight$cachedHoveredSlot != null && evernight$tooltips.shouldInspectContents(
                this.evernight$cachedHoveredSlot.getItem()) && mouseButton == 0
                && this.evernight$cachedHoveredSlot.getItem().is(ItemTags.BUNDLES)) {
            arguments.set(2, 1);
        }
    }

    @ModifyArgs(
            method = "mouseReleased",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", ordinal = 9)
    )
    private void evernight$swapBundleReleaseButton(Args arguments) {
        Slot slot = arguments.get(0);
        int mouseButton = arguments.<Integer>get(2);
        if (evernight$inventoryTweaks.isToggled() && evernight$inventoryTweaks.shouldSwapBundleClickButton(
                slot, mouseButton)) {
            arguments.set(2, mouseButton == 0 ? 1 : 0);
        }
    }

    @WrapWithCondition(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"
            ))
    private boolean evernight$renderTooltipWithEvent(
            GuiGraphics graphics, Font font, List text, Optional image, int tooltipX, int tooltipY, Identifier background
    ) {
        if (this.evernight$cachedHoveredSlot != null && evernight$tooltips.shouldInspectContents(
                this.evernight$cachedHoveredSlot.getItem())) {
            tooltipX = this.evernight$tooltipX;
            tooltipY = this.evernight$tooltipY;
        } else {
            this.evernight$tooltipX = tooltipX;
            this.evernight$tooltipY = tooltipY;
        }
        List components = TooltipComponentCapture.captureComponents(graphics, text, image);
        AbstractContainerScreenRenderTooltipEvent event = new AbstractContainerScreenRenderTooltipEvent(
                (Screen) (Object) this, components, tooltipX, tooltipY, this.evernight$mouseX, this.evernight$mouseY);
        ClientAccess.eventBus.post(event);
        if (!event.isCancelled()) {
            ((GuiGraphicsAccessor) graphics).drawTooltipsHook(font, event.getTooltipComponents(), tooltipX,
                    tooltipY, DefaultTooltipPositioner.INSTANCE, null);
        }
        return false;
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void evernight$captureSlotMapData(
            GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
        this.evernight$slotMapData = null;
        ItemStack stack = slot.getItem();
        if (stack.is(Items.FILLED_MAP)) {
            int mapId = stack.getOrDefault(DataComponents.MAP_ID, new MapId(-1)).id();
            if (mapId != -1) {
                this.evernight$slotMapId = mapId;
                this.evernight$slotMapData = ClientContext.mapDataCache.getMapData(stack, mapId);
            }
        }
    }

    @WrapWithCondition(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private boolean evernight$shouldRenderSlotItem(
            GuiGraphics graphics, ItemStack stack, int itemX, int itemY, int seed) {
        return !evernight$tooltips.isToggled() || !evernight$tooltips.mapOverlaySetting.getValue()
                || this.evernight$slotMapData == null;
    }

    @WrapWithCondition(
            method = "renderSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V")
    )
    private boolean evernight$renderMapOverlay(
            GuiGraphics graphics, Font font, ItemStack stack, int itemX, int itemY, String countText, @Local(argsOnly = true) Slot slot
    ) {
        if (!evernight$tooltips.isToggled() || !evernight$tooltips.mapOverlaySetting.getValue() || this.evernight$slotMapData == null) {
            return true;
        }
        ContainerTooltipComponent.renderMapOverlay(graphics, this.evernight$slotMapData,
                this.evernight$slotMapId, itemX, itemY);
        if (!KeyUtils.isPhysicalKeyDown(342)) {
            graphics.renderItemDecorations(font, stack, itemX, itemY);
        }
        return false;
    }
}