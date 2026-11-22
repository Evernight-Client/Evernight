package evernight.client.modules.player.InventoryTweaks;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.input.InputPressEvent;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.KeyUtils;
import evernight.mixin.minecraft.gui.AbstractContainerScreenAccessor;
import evernight.mixin.minecraft.client.KeyMappingAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Type;
import net.minecraft.world.item.ItemStack;

public class InventoryTweaksModule extends Module {
    public final Setting<Boolean> fastMoveSetting;
    public final Setting<Boolean> fastArmorSetting;
    public final Setting<Boolean> bundleTweaksSetting;
    public final Setting<Boolean> xCarryTweaksSetting;
    private final Setting<Boolean> lockSlotSetting;
    private final ElapsedTimer dropTimer = new ElapsedTimer();
    private final Setting<Boolean> onlyContainerSetting;
    private final Setting<Boolean> spamSetting;
    private int lastQuickMovedSlotId;

    public InventoryTweaksModule() {
        super("InventoryTweaks", ModuleCategory.PLAYER);
        this.lockSlotSetting = this.add(new BooleanSetting("LockSlot", false));
        BooleanSetting fastMoveOption = new BooleanSetting("FastMove", true);
        fastMoveOption.markVisibilityDependency();
        this.fastMoveSetting = this.add(fastMoveOption);
        this.onlyContainerSetting = this.add(new BooleanSetting("OnlyContainer", false).dependsOn(this.fastMoveSetting));
        this.spamSetting = this.add(new BooleanSetting("Spam", false).dependsOn(this.fastMoveSetting));
        this.fastArmorSetting = this.add(new BooleanSetting("FastArmor", false));
        this.bundleTweaksSetting = this.add(new BooleanSetting("BundleTweaks", false));
        this.xCarryTweaksSetting = this.add(new BooleanSetting("XCarryTweaks", true));
    }

    @EventHandler
    private void onDropKey(InputPressEvent event) {
        if (!this.lockSlotSetting.getValue() || this.isWorldUnavailable() || event.isMouse()) return;
        var dropKey = ((KeyMappingAccessor) ClientAccess.evernightMinecraft.options.keyDrop).getKey();
        if (dropKey.getValue() != event.getCode() || ClientAccess.evernightMinecraft.screen != null) return;
        if (ClientAccess.evernightMinecraft.player.getMainHandItem().getMaxStackSize() != 1 || !this.dropTimer.hasElapsed(300L))
            return;
        this.dropTimer.reset();
        event.cancel();
    }

    private int getClickSlotId(Slot slot) {
        CreativeModeTab selectedTab = CreativeModeInventoryScreen.selectedTab;
        boolean inventoryTab = Type.INVENTORY == selectedTab.getType();
        if (ClientAccess.evernightMinecraft.screen instanceof CreativeModeInventoryScreen && inventoryTab) {
            return slot.getContainerSlot();
        }
        return slot.index;
    }

    public boolean shouldSwapBundleClickButton(Slot slot, int mouseButton) {
        if (!this.isToggled()) {
            return false;
        }
        Boolean bundleTweaksEnabled = this.bundleTweaksSetting.getValue();
        if (!bundleTweaksEnabled || (mouseButton != 1 && mouseButton != 0) || slot == null) {
            return false;
        }
        ItemStack carriedStack = ClientAccess.evernightMinecraft.player.containerMenu.getCarried();
        TagKey bundleTag = ItemTags.BUNDLES;
        if (carriedStack.is(bundleTag)) {
            ItemStack targetStack = slot.getItem();
            if (!targetStack.isEmpty()) {
                return true;
            }
        }
        if (!carriedStack.isEmpty()) {
            ItemStack targetBundleStack = slot.getItem();
            TagKey targetBundleTag = ItemTags.BUNDLES;
            return targetBundleStack.is(targetBundleTag);
        }
        return false;
    }

    @EventHandler
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent unusedEvent) {
        Boolean fastMoveEnabled = this.fastMoveSetting.getValue();
        if (!fastMoveEnabled || !(ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen)) {
            return;
        }
        if ((this.onlyContainerSetting.getValue()) && !(ClientAccess.evernightMinecraft.screen instanceof ContainerScreen)
                && !(ClientAccess.evernightMinecraft.screen instanceof ShulkerBoxScreen)) {
            return;
        }
        ItemStack carriedStack = ClientAccess.evernightMinecraft.player.containerMenu.getCarried();
        if (!carriedStack.isEmpty()) {
            return;
        }
        Slot focusedSlot = ((AbstractContainerScreenAccessor) ClientAccess.evernightMinecraft.screen).evernight$getFocusedSlot();
        if (focusedSlot == null) {
            return;
        }
        if (focusedSlot.getItem().isEmpty()) {
            return;
        }
        Boolean spamEnabled = this.spamSetting.getValue();
        if (!spamEnabled) {
            if (this.getClickSlotId(focusedSlot) == this.lastQuickMovedSlotId) {
                return;
            }
        }
        byte mouseButton = -1;
        byte queriedMouseButton = 0;
        if (KeyUtils.isPhysicalMouseButtonDown(queriedMouseButton)) {
            mouseButton = 0;
        }
        if (KeyUtils.isPhysicalKeyDown(340) && mouseButton != -1) {
            MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
            int containerId = ClientAccess.evernightMinecraft.player.containerMenu.containerId;
            int clickedSlotId = this.getClickSlotId(focusedSlot);
            ClickType clickType = ClickType.QUICK_MOVE;
            LocalPlayer clickPlayer = ClientAccess.evernightMinecraft.player;
            gameMode.handleInventoryMouseClick(containerId, clickedSlotId, mouseButton, clickType, clickPlayer);
            this.lastQuickMovedSlotId = this.getClickSlotId(focusedSlot);
        }
    }
}