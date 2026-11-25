package evernight.client.modules.player.AutoSand;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.combat.AutoExp.AutoExpModule;
import evernight.client.modules.combat.Aura.AuraModule;
import evernight.client.modules.misc.AutoReconnect.AutoReconnectModule;
import evernight.client.modules.misc.ZenithProxy.ZenithProxyModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AuraWeaponChoice;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.inventory.AutoMiningTools;
import evernight.client.utils.inventory.ContainerInventoryMath;
import evernight.client.utils.inventory.ExperienceBottleRepairer;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.inventory.ShulkerBoxUtils;
import evernight.client.utils.inventory.StorageChainController;
import evernight.client.utils.world.BaritoneBlockTask;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.function.Predicate;
import evernight.client.modules.combat.AutoMine.AutoMineModule;
import evernight.client.modules.player.AutoEat.AutoEatModule;
import evernight.client.modules.player.AutoMining.AutoMiningModule;
import evernight.client.modules.player.AutoTool.AutoToolModule;
import evernight.client.modules.player.InventoryCleaner.InventoryCleanerModule;

/**
 * Fully automatic sand miner driven by Baritone.
 *
 * <p>Mining is left entirely to Baritone's own mine process: the module only supplies the target
 * blocks and the tool/hotbar assistance, and never restricts the Y band, exploration or dropped item
 * scanning.
 *
 * <p>When the main inventory fills up the shared {@link StorageChainController} places a shulker box
 * and moves the sand into it. An ender chest is only placed once no usable box is left in the
 * inventory, to swap a full box for an empty one. The storage spot is an existing surface 3x3x3
 * space; the miner never carves terrain for it.
 *
 * <p>A low shovel is repaired through {@link AutoExpModule}: the miner enables it with the tool
 * option, keeps the shovel in the main hand and waits until the shovel is full again. There is no
 * bottle fetching of its own, so the miner only ever uses the bottles already in the inventory.
 *
 * <p>Every linked feature (Aura, AutoTool, AutoEat, InventoryCleaner) can be switched independently
 * at runtime; only modules enabled by this module are restored when it stops.
 */
public final class AutoSandModule extends Module {
    private static final int TOOL_CHECK_INTERVAL_TICKS = 20;
    /**
     * Delay between two container clicks. Independent from the bottle throw interval: clicking a
     * container much faster than this only makes the server resync the menu.
     */
    private static final long CONTAINER_CLICK_DELAY_MILLIS = 150L;
    /**
     * Ticks without any durability gain before the repair gives up (AutoExp stopped early or the
     * shovel is not in the hand any more).
     */
    private static final int REPAIR_STALL_TICKS = 200;

    private static InventoryCleanerModule inventoryCleanerModule;
    private static AutoEatModule autoEatModule;
    private static AuraModule auraModule;
    private static AutoToolModule autoToolModule;
    private static AutoExpModule autoExpModule;

    private final Setting<Set<Block>> targetsSetting;
    private final Setting<Integer> retryTimeoutSetting;
    private final Setting<Integer> thresholdSetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<Boolean> linkInventoryCleanerSetting;
    private final Setting<Boolean> linkAuraSetting;
    private final Setting<Boolean> linkAutoToolSetting;
    private final Setting<Boolean> linkAutoEatSetting;
    private final Setting<Boolean> emergencyLogoutSetting;
    private final Setting<Boolean> debugOutputSetting;

    private final SandHost sandHost = new SandHost();
    private final StorageChainController storageChain;

    private AutoSandState state = AutoSandState.IDLE;
    private int tickCounter;
    private int miningStartedTick;
    private int miningLastActiveTick;
    private int lastToolCheckTick;
    private boolean noShulkerPrompted;

    private boolean inventoryCleanerLinked;
    private boolean auraLinked;
    private boolean autoToolLinked;
    private boolean autoEatLinked;
    private AuraWeaponChoice previousAuraWeaponMode;

    private int repairShovelSlot = -1;
    private int repairStartDamage;
    private int repairLastDamage = -1;
    private int repairStallTicks;
    private boolean autoExpRepairActive;
    private boolean autoExpWasToggled;
    private boolean autoExpToolsPrevious;
    private AutoExpModule.ModeChoice autoExpModePrevious;

    public AutoSandModule() {
        super("AutoSand", ModuleCategory.AUTO, "autosand", "sand");
        this.requiresMod("baritone", "baritone-meteor");
        this.targetsSetting = this.add(new RegistrySetSetting<>("Targets", BuiltInRegistries.BLOCK,
                Blocks.SAND, Blocks.RED_SAND));
        this.retryTimeoutSetting = this.add(new NumberSetting<>("RetryTimeout", 30, 5, 300).withUnit("s"));
        this.thresholdSetting = this.add(new NumberSetting<>("Threshold", 100, 10, 500));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
        BooleanSetting linkCleaner = new BooleanSetting("LinkInventoryCleaner", true);
        linkCleaner.setConfigName("LinkAutoDrop");
        this.linkInventoryCleanerSetting = this.add(linkCleaner);
        this.linkAuraSetting = this.add(new BooleanSetting("LinkAura", true));
        this.linkAutoToolSetting = this.add(new BooleanSetting("LinkAutoTool", true));
        this.linkAutoEatSetting = this.add(new BooleanSetting("LinkAutoEat", true));
        this.emergencyLogoutSetting = this.add(new BooleanSetting("EmergencyLogout", false));
        this.debugOutputSetting = this.add(new BooleanSetting("DebugOutput", false));

        this.targetsSetting.setChangeCallback(this::onTargetsChanged);
        this.linkInventoryCleanerSetting.setChangeCallback(this::onLinkSettingChanged);
        this.linkAuraSetting.setChangeCallback(this::onLinkSettingChanged);
        this.linkAutoToolSetting.setChangeCallback(this::onLinkSettingChanged);
        this.linkAutoEatSetting.setChangeCallback(this::onLinkSettingChanged);

        this.storageChain = new StorageChainController(this.sandHost, new BaritoneBlockTask());
    }

    // ================= lifecycle =================

    public static void bindModules(ModuleRegistry moduleRegistry) {
        inventoryCleanerModule = moduleRegistry.getModule(InventoryCleanerModule.class);
        autoEatModule = moduleRegistry.getModule(AutoEatModule.class);
        auraModule = moduleRegistry.getModule(AuraModule.class);
        autoToolModule = moduleRegistry.getModule(AutoToolModule.class);
        autoExpModule = moduleRegistry.getModule(AutoExpModule.class);
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable()) {
            this.disable();
            return;
        }
        if (!ClientContext.baritoneBridge.isAvailable()) {
            this.stopWithMessage("NoBaritone");
            return;
        }
        if (this.targetsSetting.getValue().isEmpty()) {
            this.stopWithMessage("NoTargets");
            return;
        }
        if (this.findMendingShovelSlot() == -1) {
            this.stopWithMessage("NoShovel");
            return;
        }
        if (AutoMiningTools.findSilkTouchPickaxeSlot() == -1) {
            this.notify("NoSilkTouch");
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        ClientContext.baritoneBridge.applyMiningProfile(false, false,
                this.baritoneAutoTool(), player.level().getMinY(), player.level().getMaxY());
        this.storageChain.cancel();
        this.resetState();
        this.applyLinkSettings();
        this.warnAboutConflicts();
        this.state = AutoSandState.MINING;
        this.startMining();
        this.notify("Started");
    }

    @Override
    public void onDisable() {
        this.restoreAutoExpRepair();
        this.storageChain.cancel();
        ClientContext.baritoneBridge.cancelMining();
        ClientContext.baritoneBridge.cancelPathing();
        this.restoreAllLinks();
        ClientContext.baritoneBridge.restoreMiningProfile();
        this.resetState();
        this.state = AutoSandState.IDLE;
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        this.disable();
    }

    @EventHandler
    private void onTick(LocalPlayerTickEvent event) {
        if (this.isWorldUnavailable()) {
            return;
        }
        this.tickCounter++;
        if (ClientAccess.evernightMinecraft.player.isDeadOrDying()) {
            this.disable();
            return;
        }
        switch (this.state) {
            case IDLE -> this.idle();
            case MINING -> this.mining();
            case INVENTORY_FULL -> this.inventoryFull();
            case STORAGE_CHAIN -> this.storageChainTick();
            case REPAIRING_SHOVEL -> this.repairingShovel();
        }
    }

    // ================= mining =================

    private void idle() {
        if (this.tickCounter % 2 == 0) {
            this.state = AutoSandState.MINING;
            this.startMining();
        }
    }

    private void mining() {
        if (this.startRepairIfShovelLow()) {
            return;
        }
        if (this.isInventoryFull()) {
            ClientContext.baritoneBridge.cancelMining();
            ClientContext.baritoneBridge.cancelPathing();
            this.notify("InventoryFull");
            this.state = AutoSandState.INVENTORY_FULL;
            return;
        }
        this.baritoneMining();
    }

    /**
     * Stops the mine process as soon as the shovel drops below the configured durability and hands
     * the repair over to AutoExp: the miner switches its tool option on and puts the shovel into the
     * main hand, so AutoExp's mending loop repairs it.
     *
     * @return whether the repair was started, so the caller skips the rest of its tick
     */
    private boolean startRepairIfShovelLow() {
        if (this.tickCounter - this.lastToolCheckTick < TOOL_CHECK_INTERVAL_TICKS) {
            return false;
        }
        this.lastToolCheckTick = this.tickCounter;
        int shovelSlot = this.findLowDurabilityShovel();
        if (shovelSlot == -1) {
            return false;
        }
        ClientContext.baritoneBridge.cancelMining();
        ClientContext.baritoneBridge.cancelPathing();
        this.repairShovelSlot = shovelSlot;
        this.repairStartDamage = this.shovelDamage(shovelSlot);
        this.repairLastDamage = -1;
        this.repairStallTicks = 0;
        this.holdShovel();
        this.enableAutoExpRepair();
        this.notify("ShovelLow");
        this.state = AutoSandState.REPAIRING_SHOVEL;
        return true;
    }

    private void startMining() {
        int timeoutTicks = Math.max(1, this.retryTimeoutSetting.getValue()) * 20;
        if (this.tickCounter - this.miningLastActiveTick > timeoutTicks) {
            // resuming after storage or repair: give the mine process a fresh window
            this.miningLastActiveTick = this.tickCounter;
        }
        this.miningStartedTick = this.tickCounter;
        this.lastToolCheckTick = this.tickCounter;
        Block[] blocks = this.targetBlocks();
        if (blocks.length == 0) {
            this.stopWithMessage("NoTargets");
            return;
        }
        ClientContext.baritoneBridge.mineBlocks(blocks);
    }

    private void baritoneMining() {
        if (ClientContext.baritoneBridge.isMining()) {
            this.miningLastActiveTick = this.tickCounter;
            return;
        }
        int timeoutTicks = Math.max(1, this.retryTimeoutSetting.getValue()) * 20;
        if (this.tickCounter - this.miningLastActiveTick > timeoutTicks) {
            this.retryMining();
            return;
        }
        if (this.tickCounter - this.miningStartedTick >= ContainerInventoryMath.RESTART_INTERVAL_TICKS) {
            this.startMining();
        }
    }

    /**
     * No mining for longer than the configured timeout: cancel the current process and hand the same
     * target blocks to Baritone again.
     */
    private void retryMining() {
        ClientContext.baritoneBridge.cancelMining();
        ClientContext.baritoneBridge.cancelPathing();
        this.startMining();
    }

    // ================= inventory / repair =================

    private void inventoryFull() {
        if (this.startRepairIfShovelLow()) {
            return;
        }
        if (!this.isInventoryFull()) {
            this.noShulkerPrompted = false;
            this.state = AutoSandState.MINING;
            this.startMining();
            return;
        }
        if (!ShulkerBoxUtils.hasShulkerBox()) {
            if (!this.noShulkerPrompted) {
                this.noShulkerPrompted = true;
                this.notify("StorageUnavailable");
            }
            return;
        }
        this.noShulkerPrompted = false;
        this.storageChain.beginStore();
        this.state = AutoSandState.STORAGE_CHAIN;
    }

    private void storageChainTick() {
        StorageChainController.Status status = this.storageChain.tick();
        if (status == StorageChainController.Status.DONE) {
            this.state = AutoSandState.MINING;
            this.startMining();
        } else if (status == StorageChainController.Status.FAILED) {
            this.stopWithMessage(this.storageChain.failureKey());
        }
    }

    /**
     * Waits for AutoExp to mend the held shovel back to full durability. The miner only ever uses
     * the bottles already in the inventory, so running out of them fails the repair.
     */
    private void repairingShovel() {
        ItemStack shovel = this.repairShovelStack();
        if (shovel.isEmpty()) {
            this.failShovelRepair("NoShovel");
            return;
        }
        if (shovel.getDamageValue() <= 0) {
            this.finishShovelRepair();
            return;
        }
        // AutoExp is the actual mending loop; re-enable it when the player turned it off mid-repair.
        if (autoExpModule != null && !autoExpModule.isToggled()) {
            autoExpModule.enable();
            this.autoExpWasToggled = false;
        }
        this.holdShovel();
        if (ExperienceBottleRepairer.countBottles() <= 0) {
            this.failShovelRepair("NoBottles");
            return;
        }
        int damage = shovel.getDamageValue();
        if (this.repairLastDamage >= 0 && damage < this.repairLastDamage) {
            this.repairStallTicks = 0;
        } else if (this.repairLastDamage >= 0) {
            this.repairStallTicks++;
        }
        this.repairLastDamage = damage;
        if (this.repairStallTicks > REPAIR_STALL_TICKS) {
            if (damage < this.repairStartDamage) {
                // AutoExp stopped at its own durability threshold; accept the partial repair.
                this.finishShovelRepair();
            } else {
                this.failShovelRepair("RepairFailed");
            }
        }
    }

    private void finishShovelRepair() {
        this.restoreAutoExpRepair();
        this.notify("ShovelRepaired");
        this.state = AutoSandState.MINING;
        this.startMining();
    }

    /**
     * Any failed repair stops the module: a shovel that cannot be repaired would break on the next
     * blocks and the miner could not continue anyway, so the failure is reported and the module
     * shuts down instead of silently resuming.
     */
    private void failShovelRepair(String key) {
        this.restoreAutoExpRepair();
        String failureKey = key == null || key.isEmpty() ? "RepairFailed" : key;
        this.stopWithMessage(failureKey);
    }

    /**
     * Switches AutoExp into the state the shovel repair needs: enabled, tool option on and silent
     * throwing (the ghost hand swap is what keeps the shovel in the main hand), remembering the
     * previous values so they can be restored afterwards.
     */
    private void enableAutoExpRepair() {
        if (autoExpModule == null || this.autoExpRepairActive) {
            return;
        }
        this.autoExpRepairActive = true;
        this.autoExpWasToggled = autoExpModule.isToggled();
        this.autoExpToolsPrevious = autoExpModule.toolsSetting.getValue();
        this.autoExpModePrevious = autoExpModule.modeSetting.getValue();
        autoExpModule.toolsSetting.setValue(true);
        autoExpModule.modeSetting.setValue(AutoExpModule.ModeChoice.SILENT);
        if (!this.autoExpWasToggled) {
            autoExpModule.enable();
        }
    }

    /**
     * Restores the AutoExp settings changed by {@link #enableAutoExpRepair()} and disables AutoExp
     * again when this module enabled it.
     */
    private void restoreAutoExpRepair() {
        if (!this.autoExpRepairActive || autoExpModule == null) {
            return;
        }
        this.autoExpRepairActive = false;
        autoExpModule.toolsSetting.setValue(this.autoExpToolsPrevious);
        autoExpModule.modeSetting.setValue(this.autoExpModePrevious);
        if (!this.autoExpWasToggled && autoExpModule.isToggled()) {
            autoExpModule.disable();
        }
    }

    /**
     * @return the shovel being repaired, re-locating it when the player moved it; empty when no
     * mending shovel is left
     */
    private ItemStack repairShovelStack() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (this.repairShovelSlot >= 0) {
            ItemStack stack = player.getInventory().getItem(this.repairShovelSlot);
            if (ItemUtils.isShovel(stack) && ExperienceBottleRepairer.isMendingItem(stack)) {
                return stack;
            }
        }
        this.repairShovelSlot = this.findMendingShovelSlot();
        return this.repairShovelSlot == -1 ? ItemStack.EMPTY : player.getInventory().getItem(this.repairShovelSlot);
    }

    /**
     * Puts the shovel into the main hand: a hotbar shovel is selected, one from the main inventory is
     * swapped into the selected slot.
     */
    private void holdShovel() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (this.repairShovelSlot < 0) {
            return;
        }
        ItemStack tracked = player.getInventory().getItem(this.repairShovelSlot);
        if (!tracked.isEmpty() && player.getMainHandItem() == tracked) {
            return;
        }
        if (this.repairShovelSlot < ContainerInventoryMath.HOTBAR_SIZE) {
            InventoryUtils.selectHotbarSlot(this.repairShovelSlot);
            return;
        }
        InventoryUtils.swapIntoSelectedSlot(this.repairShovelSlot);
        this.repairShovelSlot = InventoryUtils.getSelectedSlot();
    }

    private int shovelDamage(int slot) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null || slot < 0) {
            return -1;
        }
        return player.getInventory().getItem(slot).getDamageValue();
    }

    private boolean isInventoryFull() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        int[] counts = new int[ContainerInventoryMath.INVENTORY_SIZE];
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            counts[slot] = player.getInventory().getItem(slot).getCount();
        }
        if (!ContainerInventoryMath.isMainInventoryFull(counts)) {
            return false;
        }
        for (int slot = ContainerInventoryMath.HOTBAR_SIZE; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            if (this.isLootItem(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private int findLowDurabilityShovel() {
        return ExperienceBottleRepairer.findLowDurabilitySlot(ItemUtils::isShovel,
                this.thresholdSetting.getValue());
    }

    private int findMendingShovelSlot() {
        return ExperienceBottleRepairer.findMendingSlot(ItemUtils::isShovel);
    }

    // ================= links =================

    private void onTargetsChanged() {
        if (!this.isToggled() || this.isWorldUnavailable()) {
            return;
        }
        this.startMining();
    }

    private void onLinkSettingChanged() {
        if (!this.isToggled() || this.isWorldUnavailable()) {
            return;
        }
        this.applyLinkSettings();
    }

    private void applyLinkSettings() {
        if (auraModule != null) {
            if (this.linkAuraSetting.getValue()) {
                if (this.previousAuraWeaponMode == null) {
                    this.previousAuraWeaponMode = auraModule.getWeaponMode();
                }
                auraModule.applySwapWeaponMode();
                if (!auraModule.isToggled()) {
                    auraModule.enable();
                    this.auraLinked = true;
                }
            } else {
                if (this.previousAuraWeaponMode != null) {
                    auraModule.setWeaponMode(this.previousAuraWeaponMode);
                    this.previousAuraWeaponMode = null;
                }
                if (this.auraLinked && auraModule.isToggled()) {
                    auraModule.disable();
                }
                this.auraLinked = false;
            }
        }
        this.autoToolLinked = this.applyBooleanLink(this.autoToolLinked,
                this.linkAutoToolSetting.getValue(), autoToolModule);
        this.autoEatLinked = this.applyBooleanLink(this.autoEatLinked,
                this.linkAutoEatSetting.getValue(), autoEatModule);
        this.inventoryCleanerLinked = this.applyBooleanLink(this.inventoryCleanerLinked,
                this.linkInventoryCleanerSetting.getValue(), inventoryCleanerModule);
        if (!this.isWorldUnavailable()) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            ClientContext.baritoneBridge.applyMiningProfile(false, false,
                    this.baritoneAutoTool(), player.level().getMinY(), player.level().getMaxY());
        }
    }

    private boolean baritoneAutoTool() {
        return !this.linkAutoToolSetting.getValue() || autoToolModule == null;
    }

    private boolean applyBooleanLink(boolean owned, boolean wanted, Module module) {
        if (module == null) {
            return false;
        }
        if (wanted) {
            if (!module.isToggled()) {
                module.enable();
                return true;
            }
            return owned;
        }
        if (owned && module.isToggled()) {
            module.disable();
        }
        return false;
    }

    private void restoreAllLinks() {
        if (this.previousAuraWeaponMode != null && auraModule != null) {
            auraModule.setWeaponMode(this.previousAuraWeaponMode);
            this.previousAuraWeaponMode = null;
        }
        if (this.auraLinked && auraModule != null && auraModule.isToggled()) {
            auraModule.disable();
        }
        if (this.autoToolLinked && autoToolModule != null && autoToolModule.isToggled()) {
            autoToolModule.disable();
        }
        if (this.autoEatLinked && autoEatModule != null && autoEatModule.isToggled()) {
            autoEatModule.disable();
        }
        if (this.inventoryCleanerLinked && inventoryCleanerModule != null && inventoryCleanerModule.isToggled()) {
            inventoryCleanerModule.disable();
        }
        this.auraLinked = false;
        this.autoToolLinked = false;
        this.autoEatLinked = false;
        this.inventoryCleanerLinked = false;
    }

    private void warnAboutConflicts() {
        Module autoMining = ClientContext.getModule(AutoMiningModule.class);
        Module autoMine = ClientContext.getModule(evernight.client.modules.combat.AutoMine.AutoMineModule.class);
        if ((autoMining != null && autoMining.isToggled()) || (autoMine != null && autoMine.isToggled())) {
            this.notify("ConflictWarning");
        }
    }

    // ================= helpers =================

    private Block[] targetBlocks() {
        return this.targetsSetting.getValue().toArray(new Block[0]);
    }

    private boolean isLootItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return this.targetsSetting.getValue().contains(blockItem.getBlock());
    }

    private boolean isTargetBlock(BlockPos position) {
        return this.targetsSetting.getValue().contains(WorldUtils.getBlock(position));
    }

    private void resetState() {
        this.miningStartedTick = 0;
        this.miningLastActiveTick = this.tickCounter;
        this.lastToolCheckTick = 0;
        this.noShulkerPrompted = false;
        this.repairShovelSlot = -1;
        this.repairStartDamage = 0;
        this.repairLastDamage = -1;
        this.repairStallTicks = 0;
        this.autoExpRepairActive = false;
    }

    private void notify(String key, Object... args) {
        String message = Translations.format("modules.AutoSand." + key, args);
        ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this));
    }

    private void debug(String key, Object... args) {
        if (!this.debugOutputSetting.getValue()) {
            return;
        }
        this.notify(key, args);
    }

    private void stopWithMessage(String key, Object... args) {
        this.notify(key, args);
        if (this.emergencyLogoutSetting.getValue()) {
            ClientContext.taskScheduler.scheduleRunTickTask(this::logout);
        }
        this.disable();
    }

    private void logout() {
        if (ClientAccess.evernightMinecraft.getConnection() == null) {
            return;
        }
        if (ClientAccess.evernightMinecraft.getConnection().getConnection() == null) {
            return;
        }
        ZenithProxyModule zenithProxy = ClientContext.getModule(ZenithProxyModule.class);
        if (zenithProxy != null && zenithProxy.shouldCloseProxy()) {
            zenithProxy.closeProxy();
            return;
        }
        AutoReconnectModule.suppressForAutoLogout();
        String reason = Translations.format("modules.AutoSand.EmergencyLogout");
        ClientAccess.evernightMinecraft.getConnection().getConnection().disconnect(Component.literal(reason));
    }

    /**
     * Host adapter for the shared storage chain. AutoSand uses an existing surface 3x3x3 space and
     * never needs the AutoMining crafting preparation.
     */
    private final class SandHost implements StorageChainController.Host {
        @Override
        public StorageChainController.Setup storageSetup() {
            return StorageChainController.Setup.EXISTING_SPACE;
        }

        @Override
        public Predicate<ItemStack> lootFilter() {
            return AutoSandModule.this::isLootItem;
        }

        @Override
        public int storageMinY() {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            return player == null ? -64 : player.level().getMinY();
        }

        @Override
        public long clickDelayMillis() {
            return CONTAINER_CLICK_DELAY_MILLIS;
        }

        @Override
        public boolean rotatePlacement() {
            return AutoSandModule.this.rotateSetting.getValue();
        }

        @Override
        public boolean canReclaimEnderChest() {
            return AutoMiningTools.findSilkTouchPickaxeSlot() != -1;
        }

        @Override
        public boolean prepareStorage() {
            return true;
        }

        @Override
        public void notifyKey(String key, Object... args) {
            AutoSandModule.this.notify(key, args);
        }

        @Override
        public void debugKey(String key, Object... args) {
            AutoSandModule.this.debug(key, args);
        }
    }
}
