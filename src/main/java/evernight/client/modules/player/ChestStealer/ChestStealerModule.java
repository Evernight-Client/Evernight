package evernight.client.modules.player.ChestStealer;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.gui.screens.misc.ShulkerPreviewScreen;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.managers.registry.KitRegistry;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.misc.ChestSearchBar.ChestSearchBarModule;
import evernight.client.settings.options.ChestStealerLookForChoice;
import evernight.client.settings.options.ChestStealerModeChoice;
import evernight.client.settings.options.ListFilterMode;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.collections.IntPair;
import evernight.client.utils.combat.DamageUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.inventory.Kit;
import evernight.mixin.minecraft.gui.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.events.ui.AbstractContainerMenuMoveItemStackToEvent;
import evernight.client.events.ui.AbstractContainerScreenEvent;
import evernight.client.events.ui.ScreenChangeEvent;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.settings.types.StringSetSetting;

public class ChestStealerModule extends Module {
    private static ChestSearchBarModule chestSearchBar;

    static {
        chestSearchBar = ClientContext.getModule(ChestSearchBarModule.class);
    }

    public final Setting<Boolean> onlyButtonsSetting;
    public final Setting<Boolean> fullSetting;
    public final Setting<Boolean> closeSetting;
    public final Setting<Boolean> returnSetting;
    public final ShulkerOpenController openController;
    final Setting<ListFilterMode> selectSetting;
    private final Setting<Set<Item>> itemsSetting;
    private final Setting<Set<String>> namesSetting;
    private final Setting<ChestStealerModeChoice> modeSetting;
    private final Setting<ChestStealerLookForChoice> lookForSetting;
    private final Setting<Boolean> animateSetting;
    private final Setting<Integer> delaySetting;
    private final Setting<Integer> timeoutSetting;
    private final Setting<Integer> frequencySetting;
    private final ShulkerPlacementController placementController;
    private final ShulkerCleanupController cleanupController;
    private final ShulkerReturnController returnController;
    private final ShulkerRefillController refillController;
    private final List<InventoryTransferAnimation> transferAnimations;
    private final Map<Integer, Long> slotCooldowns;
    private final ElapsedTimer transferDelayTimer;
    private final ElapsedTimer screenOpenTimer;
    private final ElapsedTimer idleCloseTimer;
    private BlockPos lastInteractedBlock;
    private ChestStealerModeChoice requestedMode;
    private boolean trackingQuickMove;
    private boolean hasTransferred;
    private boolean transferredThisTick;
    private Slot quickMoveDestination;

    public ChestStealerModule() {
        super("ChestStealer", ModuleCategory.PLAYER, createAliases());
        DefaultedRegistry itemRegistry = BuiltInRegistries.ITEM;
        Item[] emptyItems = new Item[0];
        String itemsLabel = "Items";
        RegistrySetSetting<Item> itemsRegistration = new RegistrySetSetting<>(itemsLabel, itemRegistry, emptyItems);
        this.itemsSetting = this.add(itemsRegistration);
        String[] emptyNames = new String[0];
        String namesLabel = "Names";
        StringSetSetting namesRegistration = new StringSetSetting(namesLabel, emptyNames);
        this.namesSetting = this.add(namesRegistration);
        ChestStealerModeChoice defaultMode = ChestStealerModeChoice.STEAL;
        String modeLabel = "Mode";
        EnumSetting<ChestStealerModeChoice> modeRegistration = new EnumSetting<>(modeLabel, defaultMode);
        this.modeSetting = this.add(modeRegistration);
        ListFilterMode defaultSelection = ListFilterMode.ANY;
        String selectLabel = "Select";
        EnumSetting<ListFilterMode> selectRegistration = new EnumSetting<>(selectLabel, defaultSelection);
        this.selectSetting = this.add(selectRegistration);
        Predicate lookForPredicate = ignoredValue -> this.selectSetting.getValue() != ListFilterMode.ANY;
        ChestStealerLookForChoice defaultLookFor = ChestStealerLookForChoice.ITEM;
        String lookForLabel = "LookFor";
        EnumSetting<ChestStealerLookForChoice> lookForRegistration = new EnumSetting<>(lookForLabel, defaultLookFor, lookForPredicate);
        this.lookForSetting = this.add(lookForRegistration);
        Boolean onlyButtonsDefault = false;
        String onlyButtonsLabel = "OnlyButtons";
        BooleanSetting onlyButtonsRegistration = new BooleanSetting(onlyButtonsLabel, onlyButtonsDefault);
        this.onlyButtonsSetting = this.add(onlyButtonsRegistration);
        Boolean animateDefault = true;
        String animateLabel = "Animate";
        BooleanSetting animateRegistration = new BooleanSetting(animateLabel, animateDefault);
        this.animateSetting = this.add(animateRegistration);
        NumberSetting<Integer> delayRegistration = new NumberSetting<>("Delay", 50, 0, 1000);
        String delayUnit = "ms";
        delayRegistration.withUnit(delayUnit);
        this.delaySetting = this.add(delayRegistration);
        NumberSetting<Integer> timeoutRegistration = new NumberSetting<>("Timeout", 200, 0, 750);
        String timeoutUnit = "ms";
        timeoutRegistration.withUnit(timeoutUnit);
        this.timeoutSetting = this.add(timeoutRegistration);
        NumberSetting<Integer> frequencyRegistration = new NumberSetting<>("Frequency", 1, 1, 6);
        this.frequencySetting = this.add(frequencyRegistration);
        Boolean autoDefault = false;
        String autoLabel = "Auto";
        BooleanSetting autoRegistration = new BooleanSetting(autoLabel, autoDefault);
        autoRegistration.markAsGroup();
        autoRegistration.markVisibilityDependency();
        Setting<Boolean> registeredAuto = this.add(autoRegistration);
        Boolean fullDefault = false;
        String fullLabel = "Full";
        Setting<Boolean> fullWithDependency = (new BooleanSetting(fullLabel, fullDefault)).dependsOn(registeredAuto);
        this.fullSetting = this.add(fullWithDependency);
        Predicate closePredicate = ignoredValue -> !this.fullSetting.getValue();
        Boolean closeDefault = false;
        String closeLabel = "Close";
        Setting<Boolean> closeWithDependency = (new BooleanSetting(closeLabel, closeDefault, closePredicate)).dependsOn(registeredAuto);
        this.closeSetting = this.add(closeWithDependency);
        Predicate returnPredicate = ignoredValue -> !this.fullSetting.getValue();
        Boolean returnDefault = false;
        String returnLabel = "Return";
        Setting<Boolean> returnWithDependency = (new BooleanSetting(returnLabel, returnDefault, returnPredicate)).dependsOn(registeredAuto);
        this.returnSetting = this.add(returnWithDependency);
        this.placementController = new ShulkerPlacementController(this);
        this.cleanupController = new ShulkerCleanupController(this);
        this.returnController = new ShulkerReturnController(this);
        this.refillController = new ShulkerRefillController(this);
        this.openController = new ShulkerOpenController(this);
        this.transferAnimations = Collections.synchronizedList(new ArrayList<>());
        this.slotCooldowns = new HashMap<>();
        this.transferDelayTimer = new ElapsedTimer();
        this.screenOpenTimer = new ElapsedTimer();
        this.idleCloseTimer = new ElapsedTimer();
        Setting<?> itemsOption = this.itemsSetting;
        String configName = "Whitelist";
        itemsOption.setConfigName(configName);
        itemsOption = this.frequencySetting;
        SettingLabelCondition maximumLabelCondition = SettingLabelCondition.MAX;
        String maximumLabel = "Max";
        itemsOption.setBoundaryLabel(maximumLabel, maximumLabelCondition);
        itemsOption = this.animateSetting;
        Runnable clearAnimations = this.transferAnimations::clear;
        itemsOption.setChangeCallback(clearAnimations);
        itemsOption = this.modeSetting;
        Predicate modeVisibility = ignoredValue -> {
            Boolean buttonsEnabled = this.onlyButtonsSetting.getValue();
            return !buttonsEnabled;
        };
        itemsOption.addVisibilityPredicate(modeVisibility);
        Event afterInitEvent = ScreenEvents.AFTER_INIT;
        ChestStealerScreenInitializer screenInitializer = new ChestStealerScreenInitializer(this);
        afterInitEvent.register(screenInitializer);
    }

    public static BlockPos getLastInteractedBlock(ChestStealerModule module) {
        return module.lastInteractedBlock;
    }

    public static void bindReferencedModules(ModuleRegistry moduleRegistry) {
        chestSearchBar = moduleRegistry.getModule(ChestSearchBarModule.class);
    }

    private static String[] createAliases() {
        String[] aliases = new String[3];
        aliases[0] = "stealer";
        aliases[1] = "regear";
        aliases[2] = "autoregear";
        return aliases;
    }

    public ChestStealerModeChoice getRequestedMode() {
        return this.requestedMode;
    }

    public boolean shouldCloseAutomatically() {
        return this.closeSetting.getValue() || this.fullSetting.getValue();
    }

    public boolean shouldReturnAutomatically() {
        return this.returnSetting.getValue() || this.fullSetting.getValue();
    }

    public boolean hasScreenOpenTimedOut() {
        ElapsedTimer timer = this.screenOpenTimer;
        Integer timeout = this.timeoutSetting.getValue();
        return timer.hasElapsed(timeout.intValue());
    }

    private void markSlotCoolingDown(int slotIndex) {
        Map<Integer, Long> cooldowns = this.slotCooldowns;
        Integer boxedSlotIndex = slotIndex;
        long expiresAtMillis = System.currentTimeMillis() + 1000L;
        cooldowns.put(boxedSlotIndex, expiresAtMillis);
    }

    private void quickMoveAndRecordCounts(int slotIndex, List<SlotCapacity> capacities, int stackCount) {
        ClickType quickMoveType = ClickType.QUICK_MOVE;
        byte button = 0;
        InventoryUtils.clickSlot(slotIndex, button, quickMoveType);
        int remainingCount = stackCount;
        for (SlotCapacity capacityEntry : capacities) {
            if (remainingCount <= 0) {
                break;
            }
            int freeSpace = capacityEntry.getCapacity() - capacityEntry.getCount();
            if (freeSpace > 0) {
                int movedCount = Math.min(freeSpace, remainingCount);
                int updatedCount = capacityEntry.getCount() + movedCount;
                capacityEntry.setCount(updatedCount);
                remainingCount -= movedCount;
            }
        }
    }

    private boolean moveRekitStack(ItemStack stack, int sourceSlotIndex, int containerSlotCount) {
        AbstractContainerMenu menu = ClientAccess.evernightMinecraft.player.containerMenu;
        InventoryStateManager inventoryCoordinator = ClientContext.inventoryStateManager;
        if (InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, 3)) {
            return false;
        }
        if (this.isSlotCoolingDown(sourceSlotIndex)) {
            return false;
        }
        KitRegistry kitRegistry = ClientContext.kitRegistry;
        Kit profile = KitRegistry.getCurrentKit(kitRegistry);
        if (profile == null) {
            return false;
        }
        Slot sourceSlot = menu.getSlot(sourceSlotIndex);
        Iterator profileEntries = profile.getItemsBySlot().entrySet().iterator();
        while (profileEntries.hasNext()) {
            Entry profileEntry = (Entry) profileEntries.next();
            if (((Kit.ItemDescriptor) profileEntry.getValue()).matches(stack)) {
                boolean hotbarSwap = (((Integer) profileEntry.getKey())) < (9);
                int destinationOffset;
                if (hotbarSwap) {
                    Integer hotbarKey = (Integer) profileEntry.getKey();
                    destinationOffset = hotbarKey + (27);
                } else {
                    destinationOffset = (((Integer) profileEntry.getKey())) - (9);
                }
                int destinationSlotIndex = containerSlotCount + destinationOffset;
                if (!this.isSlotCoolingDown(destinationSlotIndex)) {
                    ItemStack destinationStack = menu.getSlot(destinationSlotIndex).getItem();
                    Kit.ItemDescriptor destinationDescriptor = (Kit.ItemDescriptor) profileEntry.getValue();
                    if (!destinationDescriptor.matches(destinationStack)) {
                        Item destinationItem = destinationStack.getItem();
                        if (!ItemUtils.isShulkerBox(destinationItem) || !(menu instanceof ShulkerBoxMenu)) {
                            Slot destinationSlot = menu.getSlot(destinationSlotIndex);
                            ItemStack transferredStack = sourceSlot.getItem();
                            this.recordTransferAnimation(sourceSlot, destinationSlot, transferredStack);
                            if (hotbarSwap) {
                                Integer hotbarKey = (Integer) profileEntry.getKey();
                                int hotbarButton = hotbarKey;
                                ClickType swapType = ClickType.SWAP;
                                InventoryUtils.clickSlot(sourceSlotIndex, hotbarButton, swapType);
                            } else {
                                InventoryUtils.swapSlotsWithPickup(sourceSlotIndex, destinationSlotIndex);
                            }
                            this.markSlotCoolingDown(sourceSlotIndex);
                            this.markSlotCoolingDown(destinationSlotIndex);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void refillStack(int sourceSlotIndex, List<SlotCapacity> capacities, boolean allowPartial) {
        List<SlotCapacity> availableCapacities = capacities.stream().filter(capacityEntry -> capacityEntry.getCount() < capacityEntry.getCapacity()).toList();
        if (availableCapacities.isEmpty()) {
            return;
        }
        ItemStack stack = ClientAccess.evernightMinecraft.player.containerMenu.getSlot(sourceSlotIndex).getItem();
        if (!stack.isStackable() || stack.isEmpty()) {
            return;
        }
        int stackCount = stack.getCount();
        boolean canQuickMove = InventoryUtils.isInventoryFull() || allowPartial
                || this.hasEnoughCapacity(availableCapacities, stackCount);
        InventoryStateManager inventoryCoordinator = ClientContext.inventoryStateManager;
        int clickCount;
        if (canQuickMove) {
            clickCount = 1;
        } else {
            clickCount = 3;
        }
        if (InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, clickCount)) {
            return;
        }
        if (canQuickMove) {
            this.quickMoveAndRecordCounts(sourceSlotIndex, availableCapacities, stackCount);
        } else {
            this.distributeStackWithPickup(sourceSlotIndex, availableCapacities, stackCount);
        }
    }

    @EventHandler(priority = 200)
    private void onPlayerTick(LocalPlayerPreTickEvent event) {
        if (this.fullSetting.getValue()) {
            this.cleanupController.tick();
        }
        if (this.shouldReturnAutomatically()) {
            ShulkerReturnController returnHelper = this.returnController;
            returnHelper.tick();
            returnHelper = this.returnController;
            if (returnHelper.hasReturnedThisTick()) {
                return;
            }
        }
        if (this.fullSetting.getValue()) {
            this.refillController.tick();
        }
    }

    @EventHandler
    private void onInventoryClick(MultiPlayerGameModeHandleInventoryMouseClickEvent event) {
        if (!this.shouldReturnAutomatically()) {
            return;
        }
        this.returnController.onInventoryMouseClick(event);
    }

    @EventHandler
    private void onMoveItemStack(AbstractContainerMenuMoveItemStackToEvent event) {
        if (this.trackingQuickMove) {
            ItemStack movedStack = event.getSlot().getItem();
            if (!movedStack.isEmpty()) {
                this.quickMoveDestination = event.getSlot();
            }
        }
    }

    @EventHandler
    private void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
        ClientLevel world = ClientAccess.evernightMinecraft.level;
        BlockPos blockState = event.getHitResult().getBlockPos();
        BlockState interactedState = world.getBlockState(blockState);
        Block fullOption = Blocks.ENDER_CHEST;
        if (interactedState.is(fullOption)) {
            this.lastInteractedBlock = event.getHitResult().getBlockPos();
        }
        if (this.fullSetting.getValue()) {
            this.placementController.onUseItemOn(event);
        }
    }

    public void recordTransferAnimation(Slot sourceSlot, Slot destinationSlot, ItemStack stack) {
        AbstractContainerScreenAccessor screenAccessor = (AbstractContainerScreenAccessor) ClientAccess.evernightMinecraft.screen;
        this.transferredThisTick = this.hasTransferred = true;
        if (this.animateSetting.getValue()) {
            this.transferAnimations.add(new InventoryTransferAnimation(stack,
                    new IntPair(screenAccessor.getX() + sourceSlot.x, screenAccessor.getY() + sourceSlot.y),
                    new IntPair(screenAccessor.getX() + destinationSlot.x, screenAccessor.getY() + destinationSlot.y)
            ));
        }
    }

    private List<SlotStack> collectTransferStacks(int startSlot, int endSlot, ChestStealerModeChoice mode) {
        ArrayList<SlotStack> stacks = new ArrayList<>();
        for (int slotIndex = startSlot; slotIndex < endSlot; slotIndex++) {
            ItemStack stack = ClientAccess.evernightMinecraft.player.containerMenu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()) {
                ChestSearchBarModule searchBar = chestSearchBar;
                if (!searchBar.shouldSkip(stack)) {
                    if (this.lookForSetting.getValue() == ChestStealerLookForChoice.ITEM) {
                        ListFilterMode itemFilterMode = this.selectSetting.getValue();
                        Item item = stack.getItem();
                        Setting<Set<Item>> itemsOption = this.itemsSetting;
                        if (!ListFilterMode.matches(itemFilterMode, item, itemsOption)) {
                            continue;
                        }
                    } else {
                        ListFilterMode nameFilterMode = this.selectSetting.getValue();
                        String itemName = stack.getHoverName().getString();
                        Locale locale = Locale.ROOT;
                        itemName = itemName.toLowerCase(locale);
                        Setting<Set<String>> namesOption = this.namesSetting;
                        if (!ListFilterMode.matches(nameFilterMode, itemName, namesOption)) {
                            continue;
                        }
                    }
                    stacks.add(new ChestStealerModule.SlotStack(slotIndex, stack));
                }
            }
        }
        if (mode == ChestStealerModeChoice.REFILL || mode == ChestStealerModeChoice.REKIT) {
            stacks.sort(Comparator.comparingDouble(this::transferPriority));
        }
        return stacks;
    }

    public void requestMode(ChestStealerModeChoice mode) {
        this.requestedMode = mode;
    }

    private float transferPriority(ChestStealerModule.SlotStack slotStack) {
        ItemStack stack = slotStack.stack();
        if (!stack.isStackable()) {
            ItemStack weightedStack = slotStack.stack();
            return (-DamageUtils.getDurabilityPercent(weightedStack)) / 1e+02F;
        } else {
            ItemStack countedStack = slotStack.stack();
            return -countedStack.getCount();
        }
    }

    private boolean hasEnoughCapacity(List<SlotCapacity> capacities, int requiredCount) {
        return capacities.stream().mapToInt(capacityEntry -> capacityEntry.getCapacity() - capacityEntry.getCount()).sum() >= requiredCount;
    }

    private void distributeStackWithPickup(int sourceSlotIndex, List<SlotCapacity> capacities, int remainingCount) {
        ClickType pickupClickType = ClickType.PICKUP;
        byte pickupType = 0;
        InventoryUtils.clickSlot(sourceSlotIndex, pickupType, pickupClickType);
        for (SlotCapacity capacityEntry : capacities) {
            AbstractContainerMenu menu = ClientAccess.evernightMinecraft.player.containerMenu;
            int destinationSlotIndex = capacityEntry.getSlotIndex();
            Slot destinationSlot = menu.getSlot(destinationSlotIndex);
            int clickSlotIndex = capacityEntry.getSlotIndex();
            ClickType destinationClickType = ClickType.PICKUP;
            byte clickButton = 0;
            InventoryUtils.clickSlot(clickSlotIndex, clickButton, destinationClickType);
            int movedCount = capacityEntry.getCapacity() - capacityEntry.getCount();
            int updatedCount = capacityEntry.getCount() + remainingCount;
            capacityEntry.setCount(updatedCount);
            remainingCount -= movedCount;
            Slot sourceSlot = ClientAccess.evernightMinecraft.player.containerMenu.getSlot(sourceSlotIndex);
            ItemStack animationStack = destinationSlot.getItem().copyWithCount(movedCount);
            this.recordTransferAnimation(sourceSlot, destinationSlot, animationStack);
            if (InventoryUtils.getCarriedStack().isEmpty()) {
                break;
            }
        }
        ItemStack carriedStack = InventoryUtils.getCarriedStack();
        if (!carriedStack.isEmpty()) {
            ClickType returnClickType = ClickType.PICKUP;
            byte returnButton = 0;
            InventoryUtils.clickSlot(sourceSlotIndex, returnButton, returnClickType);
        }
    }

    @EventHandler
    private void onRender(AbstractContainerScreenEvent event) {
        synchronized (this.transferAnimations) {
            this.transferAnimations.removeIf(InventoryTransferAnimation::isFinished);
            Iterator<InventoryTransferAnimation> animationIterator = this.transferAnimations.iterator();
            while (animationIterator.hasNext()) {
                InventoryTransferAnimation animation = animationIterator.next();
                GuiGraphics graphics = event.getGraphics();
                animation.render(graphics);
            }
        }
    }

    private Map<StackGroupKey, List<SlotCapacity>> collectDestinationCapacities(int startSlot) {
        HashMap<StackGroupKey, List<SlotCapacity>> capacitiesByStack = new HashMap<>();
        int relativeSlotIndex = 0;
        while (true) {
            if (relativeSlotIndex >= (36)) {
                return capacitiesByStack;
            }
            int candidateSlotIndex = relativeSlotIndex + startSlot;
            NonNullList slots = ClientAccess.evernightMinecraft.player.containerMenu.slots;
            if (candidateSlotIndex < slots.size()) {
                int menuSlotIndex = startSlot + relativeSlotIndex;
                ItemStack stack = ClientAccess.evernightMinecraft.player.containerMenu.getSlot(menuSlotIndex).getItem();
                int stackCount = stack.getCount();
                if (stackCount < stack.getMaxStackSize()) {
                    if (!stack.isEmpty()) {
                        if (stack.isStackable()) {
                            DataComponentMap components = stack.getComponents();
                            StackGroupKey key = new StackGroupKey(stack.getItem(), components);
                            capacitiesByStack.putIfAbsent(key, new ArrayList<>());
                            int maxCount = stack.getMaxStackSize();
                            List<SlotCapacity> matchingCapacities = capacitiesByStack.get(key);
                            int capacitySlotIndex = startSlot + relativeSlotIndex;
                            matchingCapacities.add(new SlotCapacity(capacitySlotIndex, maxCount, stack.getCount()));
                        }
                    }
                }
            }
            relativeSlotIndex++;
        }
    }

    @EventHandler
    private void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (event.getStage() == EventStage.Post) {
            return;
        }
        this.transferredThisTick = false;
        if (ClientAccess.evernightMinecraft.player.containerMenu == ClientAccess.evernightMinecraft.player.inventoryMenu) {
            return;
        }
        boolean shulkerScreen = ClientAccess.evernightMinecraft.screen instanceof ShulkerBoxScreen;
        Boolean onlyButtonsEnabled = this.onlyButtonsSetting.getValue();
        ChestStealerModeChoice selectedModeValue;
        if (onlyButtonsEnabled) {
            selectedModeValue = this.requestedMode;
        } else {
            selectedModeValue = this.modeSetting.getValue();
        }
        if (!(ClientAccess.evernightMinecraft.screen instanceof ContainerScreen)
                && !(ClientAccess.evernightMinecraft.screen instanceof ShulkerBoxScreen)) {
            return;
        }
        ElapsedTimer delayTimer = this.transferDelayTimer;
        long delayMillis = (this.delaySetting.getValue()).intValue();
        if (!delayTimer.hasElapsed(delayMillis)) {
            return;
        }
        if ((this.onlyButtonsSetting.getValue()) && this.requestedMode == null) {
            return;
        }
        InventoryStateManager inventoryCoordinator = ClientContext.inventoryStateManager;
        if (InventoryStateManager.isClickRateLimited(inventoryCoordinator)) {
            return;
        }
        if (!this.hasScreenOpenTimedOut() || ClientAccess.evernightMinecraft.screen instanceof ShulkerPreviewScreen) {
            return;
        }
        if (!(ClientAccess.evernightMinecraft.screen instanceof ShulkerBoxScreen)
                && selectedModeValue == ChestStealerModeChoice.REKIT) {
            Boolean requireButtons = this.onlyButtonsSetting.getValue();
            if (!requireButtons) {
                return;
            }
        }
        AbstractContainerMenu menu = ClientAccess.evernightMinecraft.player.containerMenu;
        int startSlot = 0;
        int slotCount = 27;
        int containerId = menu.containerId;
        if (ClientAccess.evernightMinecraft.screen instanceof ContainerScreen chestScreen) {
            ChestMenu chestMenu = chestScreen.getMenu();
            slotCount = (9) * chestMenu.getRowCount();
        }
        Map<StackGroupKey, List<SlotCapacity>> capacitiesByStack = this.collectDestinationCapacities(slotCount);
        boolean missingRekitSlot = false;
        if (selectedModeValue == ChestStealerModeChoice.FILL) {
            startSlot = slotCount;
            slotCount = 36;
        } else if (selectedModeValue == ChestStealerModeChoice.REKIT) {
            missingRekitSlot = this.hasFreeRekitSlot(startSlot, slotCount);
        }
        int transfers = 0;
        int endSlot = startSlot + slotCount;
        for (SlotStack slotStack : this.collectTransferStacks(startSlot, endSlot, selectedModeValue)) {
            ItemStack stack = slotStack.stack();
            int slotIndex = slotStack.slotIndex();
            Item item = stack.getItem();
            List<SlotCapacity> matchingCapacities = capacitiesByStack.get(new StackGroupKey(item, stack.getComponents()));
            switch (selectedModeValue) {
                case DROP:
                    InventoryStateManager dropCoordinator = ClientContext.inventoryStateManager;
                    byte dropClickCount = 1;
                    if (InventoryStateManager.wouldReachClickLimit(dropCoordinator, dropClickCount)) {
                        continue;
                    }
                    MultiPlayerGameMode throwGameMode = ClientAccess.evernightMinecraft.gameMode;
                    byte throwButton = 1;
                    ClickType throwType = ClickType.THROW;
                    LocalPlayer throwPlayer = ClientAccess.evernightMinecraft.player;
                    throwGameMode.handleInventoryMouseClick(containerId, slotIndex, throwButton, throwType, throwPlayer);
                    this.transferredThisTick = this.hasTransferred = true;
                    break;
                case FILL:
                case STEAL:
                    if (shulkerScreen) {
                        Item shulkerItem = stack.getItem();
                        if (ItemUtils.isShulkerBox(shulkerItem)) {
                            continue;
                        }
                    }
                    InventoryStateManager quickMoveCoordinator = ClientContext.inventoryStateManager;
                    byte quickMoveClickCount = 1;
                    if (InventoryStateManager.wouldReachClickLimit(quickMoveCoordinator, quickMoveClickCount)) {
                        continue;
                    }
                    this.quickMoveDestination = null;
                    this.trackingQuickMove = true;
                    MultiPlayerGameMode quickMoveGameMode = ClientAccess.evernightMinecraft.gameMode;
                    byte quickMoveButton = 0;
                    ClickType quickMoveType = ClickType.QUICK_MOVE;
                    LocalPlayer quickMovePlayer = ClientAccess.evernightMinecraft.player;
                    quickMoveGameMode.handleInventoryMouseClick(containerId, slotIndex, quickMoveButton, quickMoveType, quickMovePlayer);
                    this.trackingQuickMove = false;
                    if (this.quickMoveDestination != null) {
                        Slot sourceSlot = menu.getSlot(slotIndex);
                        Slot destinationSlot = this.quickMoveDestination;
                        ItemStack movedStack = destinationSlot.getItem();
                        this.recordTransferAnimation(sourceSlot, destinationSlot, movedStack);
                    }
                    break;
                case REFILL:
                    if (matchingCapacities == null) {
                        continue;
                    }
                    this.refillStack(slotIndex, matchingCapacities, false);
                    break;
                case REKIT:
                    if (matchingCapacities != null && !missingRekitSlot) {
                        this.refillStack(slotIndex, matchingCapacities, false);
                    } else {
                        if (!this.moveRekitStack(stack, slotIndex, slotCount)) {
                            continue;
                        }
                    }
            }
            this.transferDelayTimer.reset();
            InventoryStateManager postTransferCoordinator = ClientContext.inventoryStateManager;
            if (InventoryStateManager.isClickRateLimited(postTransferCoordinator)) {
                break;
            }
            transfers++;
            Integer frequencyValue = this.frequencySetting.getValue();
            if (transfers >= frequencyValue) {
                Setting<Integer> frequencyOption = this.frequencySetting;
                if (!frequencyOption.isAtMaximum()) {
                    break;
                }
            }
        }
        if (this.transferredThisTick) {
            this.idleCloseTimer.reset();
        }
        if (this.idleCloseTimer.hasElapsed(100L) && this.hasTransferred) {
            if (this.shouldCloseAutomatically()) {
                int closeDelay = (int) ((ClientContext.serverStateManager.getLatencyMillis() / 5e+01F) + 1.0F);
                ClientAccess.evernightMinecraft.setScreen(null);
                TaskScheduler closeScheduler = ClientContext.taskScheduler;
                Runnable closeContainer = () -> {
                    if (ClientAccess.evernightMinecraft.player != null
                            && ClientAccess.evernightMinecraft.player.containerMenu == menu) {
                        ClientAccess.evernightMinecraft.player.closeContainer();
                    }
                };
                closeScheduler.scheduleRotationTask(closeContainer, closeDelay);
            }
        }
    }

    @EventHandler
    private void onRotationUpdate(RotationRequestEvent event) {
        if (this.shouldReturnAutomatically()) {
            this.returnController.reopenRequestedShulker();
        }
        if (this.fullSetting.getValue()) {
            this.openController.openPendingShulker();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundContainerSetSlotPacket packet) {
            if (this.shouldReturnAutomatically()) {
                Minecraft animationMinecraft = ClientAccess.evernightMinecraft;
                Runnable recordAnimation = () -> {
                    ShulkerReturnController returnHelper = this.returnController;
                    int containerId = packet.getContainerId();
                    ItemStack updatedStack = packet.getItem();
                    returnHelper.onContainerSlotUpdate(containerId, updatedStack);
                };
                animationMinecraft.execute(recordAnimation);
            }
        }
    }

    @EventHandler
    private void onScreenChange(ScreenChangeEvent.AfterInit event) {
        this.hasTransferred = false;
        this.screenOpenTimer.reset();
        this.transferAnimations.clear();
        this.slotCooldowns.clear();
    }

    private boolean isSlotCoolingDown(int slotIndex) {
        Long expiresAtMillis = this.slotCooldowns.get(slotIndex);
        if (expiresAtMillis != null) {
            return System.currentTimeMillis() < expiresAtMillis;
        }
        return false;
    }

    private boolean hasFreeRekitSlot(int startSlot, int slotCount) {
        AbstractContainerMenu menu = ClientAccess.evernightMinecraft.player.containerMenu;
        KitRegistry kitRegistry = ClientContext.kitRegistry;
        Kit profile = KitRegistry.getCurrentKit(kitRegistry);
        if (profile == null) {
            return false;
        }
        for (int relativeSlotIndex = startSlot; relativeSlotIndex < startSlot + slotCount; relativeSlotIndex++) {
            if (!this.isSlotCoolingDown(relativeSlotIndex)) {
                ItemStack sourceStack = menu.getSlot(relativeSlotIndex).getItem();
                Iterator profileEntries = profile.getItemsBySlot().entrySet().iterator();
                while (profileEntries.hasNext()) {
                    Entry profileEntry = (Entry) profileEntries.next();
                    if (((Kit.ItemDescriptor) profileEntry.getValue()).matches(sourceStack)) {
                        boolean hotbarDestination = (((Integer) profileEntry.getKey())) < 9;
                        int destinationOffset;
                        if (hotbarDestination) {
                            destinationOffset = (((Integer) profileEntry.getKey())) + 27;
                        } else {
                            destinationOffset = (((Integer) profileEntry.getKey())) - (9);
                        }
                        int destinationSlotIndex = slotCount + destinationOffset;
                        if (!this.isSlotCoolingDown(destinationSlotIndex)) {
                            ItemStack destinationStack = menu.getSlot(destinationSlotIndex).getItem();
                            Kit.ItemDescriptor destinationDescriptor = (Kit.ItemDescriptor) profileEntry.getValue();
                            if (!destinationDescriptor.matches(destinationStack)) {
                                Item destinationItem = destinationStack.getItem();
                                if (!ItemUtils.isShulkerBox(destinationItem)) {
                                    if (((Kit.ItemDescriptor) profileEntry.getValue()).itemId() != 0) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public record SlotStack(int slotIndex, ItemStack stack) {
    }
}