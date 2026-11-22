package evernight.client.modules.player.InventorySorter;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.managers.registry.KitRegistry;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.combat.Mainhand.MainhandModule;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.InventorySorterModeChoice;
import evernight.client.settings.options.InventorySorterScopeChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.inventory.InventorySortStrategy;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.stream.IntStream;

public class InventorySorterModule extends Module {
    private static MultiTaskModule multiTaskModule = ClientContext.getModule(MultiTaskModule.class);
    private static MainhandModule mainhandModule = ClientContext.getModule(MainhandModule.class);
    private final Setting<InventorySorterScopeChoice> scopeSetting;
    private final Setting<InventorySorterModeChoice> modeSetting;
    private final Setting<Integer> delaySetting;
    private final Setting<Integer> frequencySetting;
    private final Setting<Boolean> silentSetting;
    private final Setting<Boolean> autoDisableSetting;
    private final ElapsedTimer sortTimer;

    public InventorySorterModule() {
        super("InventorySorter", ModuleCategory.PLAYER);
        InventorySorterScopeChoice defaultScope = InventorySorterScopeChoice.INVENTORY;
        this.scopeSetting = this.add(new EnumSetting<>("Scope", defaultScope));
        InventorySorterModeChoice defaultMode = InventorySorterModeChoice.TYPE;
        this.modeSetting = this.add(new EnumSetting<>("Mode", defaultMode));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 50, 50, 500).withUnit("ms"));
        this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 3));
        this.silentSetting = this.add(new BooleanSetting("Silent", false));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
        this.sortTimer = new ElapsedTimer();
    }

    public static void bindModules(ModuleRegistry moduleRegistry) {
        multiTaskModule = moduleRegistry.getModule(MultiTaskModule.class);
        mainhandModule = moduleRegistry.getModule(MainhandModule.class);
    }

    private IntStream getTargetSlots(InventorySorterScopeChoice scope, InventorySorterModeChoice mode) {
        if (scope == InventorySorterScopeChoice.BOTH && mode != InventorySorterModeChoice.KIT) {
            int firstStart = scope.getStartInclusive();
            int firstEnd = scope.getEndExclusive();
            IntStream inventoryFirst = IntStream.range(firstStart, firstEnd).skip(9L);
            int secondStart = scope.getStartInclusive();
            int secondEnd = scope.getEndExclusive();
            IntStream hotbarLast = IntStream.range(secondStart, secondEnd).limit(9L);
            return IntStream.concat(inventoryFirst, hotbarLast);
        }
        int startInclusive = scope.getStartInclusive();
        int endExclusive = scope.getEndExclusive();
        return IntStream.range(startInclusive, endExclusive);
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
        if (shouldSkipSorting()) {
            return;
        }
        int protectedSlot = mainhandModule.getConfiguredHotbarSlot();
        InventorySorterModeChoice mode = this.modeSetting.getValue();
        InventorySorterScopeChoice scope = this.scopeSetting.getValue();
        Integer[] sourceSlots = mode.getStrategy().getSourceSlots(scope);
        int[] targetSlots = getTargetSlots(scope, mode).toArray();
        int attemptedSwaps = 0;
        for (int pairIndex = 0; ; pairIndex++) {
            int sourceCount = sourceSlots.length;
            int targetCount = targetSlots.length;
            if (pairIndex >= Math.min(sourceCount, targetCount)) {
                break;
            }
            if (mainhandModule.isToggled()) {
                Integer protectedSource = sourceSlots[pairIndex];
                if (protectedSlot == protectedSource || protectedSlot == targetSlots[pairIndex]) {
                    continue;
                }
            }
            int sourceSlot = (sourceSlots[pairIndex]);
            ItemStack sourceStack = InventorySortStrategy.getInventoryStack(sourceSlot);
            int targetSlot = targetSlots[pairIndex];
            ItemStack targetStack = InventorySortStrategy.getInventoryStack(targetSlot);
            if ((sourceSlots[pairIndex]) != targetSlots[pairIndex] && !ItemStack.isSameItem(sourceStack, targetStack)) {
                int targetInventorySlot = targetSlots[pairIndex];
                int targetMenuSlot = InventoryUtils.toMenuSlot(targetInventorySlot);
                int sourceInventorySlot = (sourceSlots[pairIndex]);
                int sourceMenuSlot = InventoryUtils.toMenuSlot(sourceInventorySlot);
                executeSlotSwap(targetMenuSlot, sourceMenuSlot);
                attemptedSwaps++;
            }
            Integer frequency = this.frequencySetting.getValue();
            if (attemptedSwaps >= frequency) {
                break;
            }
            sourceSlots = mode.getStrategy().getSourceSlots(scope);
        }
        if (attemptedSwaps == 0) {
            if (this.autoDisableSetting.getValue()) {
                disable();
            }
        }
    }

    private void executeSlotSwap(int firstSlot, int secondSlot) {
        InventoryStateManager initialManager = ClientContext.inventoryStateManager;
        if (InventoryStateManager.wouldReachClickLimit(initialManager, (byte) 1)) {
            return;
        }
        this.sortTimer.reset();
        if (firstSlot >= (36)) {
            if (secondSlot >= (36)) {
                int hotbarButton = secondSlot - (36);
                InventoryUtils.swapWithHotbarSlot(firstSlot, hotbarButton);
                return;
            }
        }
        if (firstSlot >= (36)) {
            int firstButton = firstSlot - (36);
            InventoryUtils.swapWithHotbarSlot(secondSlot, firstButton);
        } else {
            if (secondSlot >= (36)) {
                int secondButton = secondSlot - (36);
                InventoryUtils.swapWithHotbarSlot(firstSlot, secondButton);
            } else {
                InventoryStateManager fallbackManager = ClientContext.inventoryStateManager;
                if (!InventoryStateManager.wouldReachClickLimit(fallbackManager, 3)) {
                    InventoryUtils.swapSlotsWithPickup(firstSlot, secondSlot);
                }
            }
        }
    }

    private boolean shouldSkipSorting() {
        ItemStack carriedStack = InventoryUtils.getCarriedStack();
        if (!carriedStack.isEmpty()) {
            return true;
        }
        if (multiTaskModule.shouldPauseModule(this)) {
            return true;
        }
        if (ClientAccess.evernightMinecraft.player.inventoryMenu != ClientAccess.evernightMinecraft.player.containerMenu) {
            return true;
        }
        if (this.modeSetting.getValue() == InventorySorterModeChoice.KIT) {
            KitRegistry kitOwner = ClientContext.kitRegistry;
            if (KitRegistry.getCurrentKit(kitOwner) == null) {
                return true;
            }
        }
        Boolean silent = this.silentSetting.getValue();
        if (!silent && !(ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen)) {
            return true;
        }
        ElapsedTimer timer = this.sortTimer;
        long delayMillis = (this.delaySetting.getValue()).intValue();
        return !timer.hasElapsed(delayMillis);
    }
}