package evernight.client.modules.player.AutoGrindstone;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.inventory.EnchantmentUtils;
import evernight.client.utils.inventory.InventoryUtils;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Strips enchantments from the player inventory while a grindstone screen is open.
 *
 * <p>Ported from LPCTools' auto grindstone tool. Every enchanted single stack in the main inventory
 * or hotbar is moved into the grindstone and, once the result is ready, dropped (or moved back into
 * the inventory when {@code Drop} is off). When nothing is left to strip the screen is closed.
 *
 * <p>The module only ever touches the grindstone while it is empty, so a grindstone the player is
 * using manually is never disturbed. Curses are not special cased: the grindstone keeps them and
 * removes the remaining enchantments.
 */
public final class AutoGrindstoneModule extends Module {
    private static final long RESULT_TIMEOUT_MILLIS = 1000L;
    private static final int MAIN_INVENTORY_OFFSET = 6;
    private static final int HOTBAR_OFFSET = 30;
    private static final int HOTBAR_SIZE = 9;
    private static final int NON_EQUIPMENT_SLOT_COUNT = 36;
    private static final int MAX_STACK_FOR_STRIPPING = 1;

    private final Setting<Integer> delaySetting;
    private final Setting<Boolean> dropSetting;
    private final Setting<Boolean> closeSetting;
    private final ElapsedTimer stepTimer;
    private final ElapsedTimer resultTimer;
    private int containerId;
    private boolean awaitingResult;
    private boolean exhausted;

    public AutoGrindstoneModule() {
        super("AutoGrindstone",
                ModuleCategory.PLAYER);
        this.delaySetting = this.add(new NumberSetting<>("Delay", 100, 0, 1000).withUnit("ms"));
        this.dropSetting = this.add(new BooleanSetting("Drop", true));
        this.closeSetting = this.add(new BooleanSetting("Close", true));
        this.stepTimer = new ElapsedTimer();
        this.resultTimer = new ElapsedTimer();
        this.containerId = -1;
    }

    private static int toGrindstoneSlot(int inventorySlot) {
        return inventorySlot < HOTBAR_SIZE ? inventorySlot + HOTBAR_OFFSET : inventorySlot - MAIN_INVENTORY_OFFSET;
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerPreTickEvent event) {
        if (this.isWorldUnavailable() || !(ClientAccess.evernightMinecraft.screen instanceof GrindstoneScreen)
                || !(ClientAccess.evernightMinecraft.player.containerMenu instanceof GrindstoneMenu menu)) {
            this.resetState();
            return;
        }
        if (this.containerId != menu.containerId) {
            this.containerId = menu.containerId;
            this.awaitingResult = false;
            this.exhausted = false;
            this.stepTimer.clear();
            this.resultTimer.clear();
        }
        if (this.exhausted || !this.stepTimer.hasElapsed((this.delaySetting.getValue()).longValue())) {
            return;
        }
        this.tickGrindstone(menu);
    }

    private void tickGrindstone(GrindstoneMenu menu) {
        ItemStack resultStack = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        if (this.awaitingResult) {
            if (!resultStack.isEmpty()) {
                this.collectResult(menu);
                boolean resultMoved = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty();
                if (resultMoved) {
                    this.awaitingResult = false;
                    this.stepTimer.reset();
                    return;
                }
                if (this.resultTimer.hasElapsed(RESULT_TIMEOUT_MILLIS)) {
                    this.finish();
                }
                return;
            }
            if (this.isGrindstoneEmpty(menu)) {
                this.awaitingResult = false;
                this.stepTimer.reset();
                return;
            }
            if (this.resultTimer.hasElapsed(RESULT_TIMEOUT_MILLIS)) {
                this.finish();
            }
            return;
        }
        if (!this.isGrindstoneEmpty(menu)) {
            return;
        }
        int sourceSlot = this.findSourceSlot();
        if (sourceSlot < 0) {
            this.finish();
            return;
        }
        if (InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 1)) {
            return;
        }
        InventoryUtils.clickSlot(menu.containerId, sourceSlot, 0, ClickType.QUICK_MOVE);
        this.awaitingResult = true;
        this.stepTimer.reset();
        this.resultTimer.reset();
    }

    private void collectResult(GrindstoneMenu menu) {
        if (this.dropSetting.getValue()) {
            InventoryUtils.clickSlot(menu.containerId, GrindstoneMenu.RESULT_SLOT, 1, ClickType.THROW);
        } else {
            InventoryUtils.clickSlot(menu.containerId, GrindstoneMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE);
        }
    }

    private boolean isGrindstoneEmpty(GrindstoneMenu menu) {
        return menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem().isEmpty()
                && menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).getItem().isEmpty()
                && menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty();
    }

    private int findSourceSlot() {
        Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
        for (int inventorySlot = 0; inventorySlot < NON_EQUIPMENT_SLOT_COUNT; inventorySlot++) {
            ItemStack stack = inventory.getItem(inventorySlot);
            if (stack.getCount() != MAX_STACK_FOR_STRIPPING || !this.hasEnchantments(stack)) {
                continue;
            }
            return toGrindstoneSlot(inventorySlot);
        }
        return -1;
    }

    private boolean hasEnchantments(ItemStack stack) {
        return !EnchantmentUtils.getLevels(stack).isEmpty();
    }

    private void finish() {
        this.exhausted = true;
        if (!this.closeSetting.getValue()) {
            return;
        }
        Screen screen = ClientAccess.evernightMinecraft.screen;
        if (screen != null) {
            screen.onClose();
        }
    }

    private void resetState() {
        this.containerId = -1;
        this.awaitingResult = false;
        this.exhausted = false;
        this.stepTimer.clear();
        this.resultTimer.clear();
    }
}
