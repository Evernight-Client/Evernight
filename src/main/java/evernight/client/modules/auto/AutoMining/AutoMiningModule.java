package evernight.client.modules.auto.AutoMining;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.combat.Aura.AuraModule;
import evernight.client.modules.misc.AutoReconnect.AutoReconnectModule;
import evernight.client.modules.misc.ZenithProxy.ZenithProxyModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AuraWeaponChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.inventory.*;
import evernight.client.utils.inventory.ContainerInventoryMath.MenuKind;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.AutoMiningEscape;
import evernight.client.utils.world.BaritoneBlockTask;
import evernight.client.utils.world.StoragePlacement;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import evernight.client.modules.combat.AutoMine.AutoMineModule;
import evernight.client.modules.auto.AutoEat.AutoEatModule;
import evernight.client.modules.player.AutoTool.AutoToolModule;
import evernight.client.modules.player.InventoryCleaner.InventoryCleanerModule;
import evernight.client.modules.player.Nuker.NukerModule;

/**
 * Fully automatic ore miner.
 *
 * <p>Mining itself is delegated to Baritone, and so is every block the storage chain has to place or break
 * again: the pocket is carved with a builder clear area, the crafting table, shulker box and ender chest
 * are placed through a one block schematic and recovered with Baritone as well. The module only holds
 * the block on the hotbar first (the builder never reads the main inventory) and talks to container
 * screens (which Baritone cannot do) to keep the loot flowing.
 *
 * <p>Tool selection is not done here: the AutoTool module is enabled alongside (setting
 * {@code LinkAutoTool}) and owns the hand, including its ore enchantment choice (fortune or silk touch)
 * and the "silk touch for ender chests" rule. Baritone's own auto tool stays off so the two never fight
 * over the hotbar.
 */
public final class AutoMiningModule extends Module {
    private static InventoryCleanerModule inventoryCleanerModule;
    private static AutoEatModule autoEatModule;
    private static AuraModule auraModule;
    private static AutoToolModule autoToolModule;

    public final Setting<AutoMiningMode> modeSetting;
    public final Setting<Integer> minYSetting;
    public final Setting<Integer> maxYSetting;
    private final Setting<Integer> pickaxeThresholdSetting;
    private final Setting<Boolean> avoidMobsSetting;
    private final Setting<Boolean> avoidBlocksSetting;
    private final Setting<Boolean> avoidDeepDarkSetting;
    private final Setting<Boolean> placeRotateSetting;
    private final Setting<Integer> craftDelaySetting;
    private final Setting<Boolean> debugOutputSetting;
    private final Setting<Boolean> linkInventoryCleanerSetting;
    private final Setting<Boolean> linkAuraSetting;
    private final Setting<Boolean> linkAutoToolSetting;
    private final Setting<Boolean> linkAutoEatSetting;
    private final Setting<Boolean> emergencyLogoutSetting;

    private final ElapsedTimer actionTimer = new ElapsedTimer();
    private final BaritoneBlockTask craftingTask = new BaritoneBlockTask();
    private final StorageChainController storageChain;
    private final StorageHost storageHost = new StorageHost();

    private AutoMiningState state = AutoMiningState.IDLE;
    private AutoMiningState resumeState = AutoMiningState.MINING;
    private int stateWaitTicks;
    private int tickCounter;
    private int miningStartedTick;
    private int lastPickaxeCheckTick;
    private int openContainerTicks;
    private int craftAttempts;
    private int silentQuartzAttempts;
    private int quartzPlacementAttempts;
    private int quartzCraftFailures;
    private int quartzCooldownUntilTick;
    private int repairPickaxeSlot = -1;

    private boolean quartzScreenPhase;
    private boolean craftingAvailable = true;
    private boolean noShulkerPrompted;
    private boolean inventoryCleanerLinked;
    private boolean auraLinked;
    private boolean autoToolLinked;
    private boolean autoEatLinked;
    private AuraWeaponChoice previousAuraWeaponMode;
    private String appliedYRange;
    private Item storageTarget = Items.DIAMOND;
    private BlockPos placedPos;
    private BlockPos pendingTablePos;
    private BlockPos escapeTarget;

    public AutoMiningModule() {
        super("AutoMining", ModuleCategory.AUTO, "automining", "am");
        this.requiresMod("baritone", "baritone-meteor");
        this.modeSetting = this.add(new EnumSetting<>("MiningMode", AutoMiningMode.DIAMOND));
        this.minYSetting = this.add(new NumberSetting<>("MinY", -60, -64, 320));
        this.maxYSetting = this.add(new NumberSetting<>("MaxY", 16, -64, 320));
        Predicate<Integer> pickaxeThresholdVisibility = unused -> this.modeSetting.getValue() == AutoMiningMode.ANCIENT_DEBRIS;
        this.pickaxeThresholdSetting = this.add(new NumberSetting<>("PickaxeThreshold", 200, 50, 500, pickaxeThresholdVisibility));
        this.avoidMobsSetting = this.add(new BooleanSetting("AvoidMobs", true));
        this.avoidBlocksSetting = this.add(new BooleanSetting("AvoidBlocks", true));
        Predicate<Boolean> avoidDeepDarkVisibility = unused -> this.modeSetting.getValue() == AutoMiningMode.DIAMOND;
        this.avoidDeepDarkSetting = this.add(new BooleanSetting("AvoidDeepDark", true, avoidDeepDarkVisibility));
        this.placeRotateSetting = this.add(new BooleanSetting("PlaceRotate", true));
        this.craftDelaySetting = this.add(new NumberSetting<>("CraftDelay", 150, 0, 1500).withUnit("ms"));
        this.debugOutputSetting = this.add(new BooleanSetting("DebugOutput", false));
        BooleanSetting linkInventoryCleaner = new BooleanSetting("LinkInventoryCleaner", true);
        linkInventoryCleaner.setConfigName("LinkAutoDrop");
        this.linkInventoryCleanerSetting = this.add(linkInventoryCleaner);
        this.linkAuraSetting = this.add(new BooleanSetting("LinkAura", true));
        this.linkAutoToolSetting = this.add(new BooleanSetting("LinkAutoTool", true));
        this.linkAutoEatSetting = this.add(new BooleanSetting("LinkAutoEat", true));
        this.emergencyLogoutSetting = this.add(new BooleanSetting("EmergencyLogout", false));
        this.avoidMobsSetting.hideWithoutBaritone();
        this.avoidBlocksSetting.hideWithoutBaritone();
        this.modeSetting.setChangeCallback(this::onModeChanged);
        this.minYSetting.setChangeCallback(this::onYRangeChanged);
        this.maxYSetting.setChangeCallback(this::onYRangeChanged);
        this.storageChain = new StorageChainController(this.storageHost, new BaritoneBlockTask());
    }

    // ================= lifecycle =================

    public static void bindModules(ModuleRegistry moduleRegistry) {
        inventoryCleanerModule = moduleRegistry.getModule(InventoryCleanerModule.class);
        autoEatModule = moduleRegistry.getModule(AutoEatModule.class);
        auraModule = moduleRegistry.getModule(AuraModule.class);
        autoToolModule = moduleRegistry.getModule(AutoToolModule.class);
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
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        boolean nether = player.level().dimension() == Level.NETHER;
        AutoMiningMode mode = this.modeSetting.getValue();
        if (mode == AutoMiningMode.DIAMOND && nether) {
            this.stopWithMessage("WrongDimension");
            return;
        }
        if (mode == AutoMiningMode.ANCIENT_DEBRIS && !nether) {
            this.stopWithMessage("WrongDimension");
            return;
        }
        if (AutoMiningTools.findFortunePickaxeSlot() == -1) {
            this.stopWithMessage("MissingFortunePickaxe");
            return;
        }
        if (AutoMiningTools.findSilkTouchPickaxeSlot() == -1) {
            this.notify("NoSilkTouch");
        }
        this.warnAboutConflicts();

        ClientContext.baritoneBridge.applyMiningProfile(
                this.avoidMobsSetting.getValue(),
                this.avoidBlocksSetting.getValue(),
                false,
                this.minYSetting.getValue(),
                this.maxYSetting.getValue());
        this.resetState();
        this.updateStorageTarget();
        this.linkModules();
        this.state = AutoMiningState.MINING;
        this.startMining();
        this.notify("Started", this.modeSetting.getValue().getDisplayName());
    }

    @Override
    public void onDisable() {
        if (this.hasManagedScreenOpen()) {
            ContainerInteractionUtils.closeScreen();
        }
        this.storageChain.cancel();
        this.craftingTask.cancel();
        ClientContext.baritoneBridge.cancelMining();
        ClientContext.baritoneBridge.cancelPathing();
        ClientContext.baritoneBridge.restoreMiningProfile();
        this.unlinkModules();
        this.resetState();
        this.state = AutoMiningState.IDLE;
    }

    /**
     * @return whether the open screen was opened by the storage chain or by the quartz crafting, so it is
     * safe to close it on disable instead of closing whatever the player had open
     */
    private boolean hasManagedScreenOpen() {
        if (ClientAccess.evernightMinecraft.screen == null) {
            return false;
        }
        if (this.storageChain.hasManagedScreen()) {
            return true;
        }
        return this.state == AutoMiningState.CRAFTING || this.state == AutoMiningState.REPAIRING_PICKAXE;
    }

    private void resetState() {
        this.storageChain.cancel();
        this.craftingTask.cancel();
        this.resumeState = AutoMiningState.MINING;
        this.stateWaitTicks = 0;
        this.tickCounter = 0;
        this.miningStartedTick = 0;
        this.lastPickaxeCheckTick = 0;
        this.openContainerTicks = 0;
        this.craftAttempts = 0;
        this.silentQuartzAttempts = 0;
        this.quartzPlacementAttempts = 0;
        this.quartzCraftFailures = 0;
        this.quartzCooldownUntilTick = 0;
        this.repairPickaxeSlot = -1;
        this.quartzScreenPhase = false;
        this.craftingAvailable = true;
        this.noShulkerPrompted = false;
        this.appliedYRange = null;
        this.placedPos = null;
        this.pendingTablePos = null;
        this.escapeTarget = null;
        AutoMiningCrafting.invalidateCache();
    }

    private void linkModules() {
        if (this.linkInventoryCleanerSetting.getValue()
                && inventoryCleanerModule != null && !inventoryCleanerModule.isToggled()) {
            inventoryCleanerModule.enable();
            this.inventoryCleanerLinked = true;
        }
        if (this.linkAuraSetting.getValue() && auraModule != null) {
            if (this.previousAuraWeaponMode == null) {
                this.previousAuraWeaponMode = auraModule.getWeaponMode();
            }
            auraModule.applySwapWeaponMode();
            if (!auraModule.isToggled()) {
                auraModule.enable();
                this.auraLinked = true;
            }
        }
        if (this.linkAutoToolSetting.getValue() && autoToolModule != null && !autoToolModule.isToggled()) {
            autoToolModule.enable();
            this.autoToolLinked = true;
        }
        if (this.linkAutoEatSetting.getValue() && autoEatModule != null && !autoEatModule.isToggled()) {
            autoEatModule.enable();
            this.autoEatLinked = true;
        }
    }

    private void unlinkModules() {
        if (this.inventoryCleanerLinked && inventoryCleanerModule != null && inventoryCleanerModule.isToggled()) {
            inventoryCleanerModule.disable();
        }
        if (this.auraLinked && auraModule != null && auraModule.isToggled()) {
            auraModule.disable();
        }
        if (this.previousAuraWeaponMode != null && auraModule != null) {
            auraModule.setWeaponMode(this.previousAuraWeaponMode);
            this.previousAuraWeaponMode = null;
        }
        if (this.autoToolLinked && autoToolModule != null && autoToolModule.isToggled()) {
            autoToolModule.disable();
        }
        if (this.autoEatLinked && autoEatModule != null && autoEatModule.isToggled()) {
            autoEatModule.disable();
        }
        this.inventoryCleanerLinked = false;
        this.auraLinked = false;
        this.autoToolLinked = false;
        this.autoEatLinked = false;
    }

    // ================= events =================

    private void warnAboutConflicts() {
        Module nuker = ClientContext.getModule(NukerModule.class);
        Module autoMine = ClientContext.getModule(evernight.client.modules.combat.AutoMine.AutoMineModule.class);
        if ((nuker != null && nuker.isToggled()) || (autoMine != null && autoMine.isToggled())) {
            this.notify("ConflictWarning");
        }
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        this.disable();
    }

    // ================= mining =================

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
        if (this.modeSetting.getValue() == AutoMiningMode.DIAMOND
                && this.avoidDeepDarkSetting.getValue()
                && (this.state == AutoMiningState.MINING || this.state == AutoMiningState.IDLE)
                && AutoMiningEscape.isDeepDark(ClientAccess.evernightMinecraft.player.blockPosition())) {
            this.storageChain.cancel();
            this.craftingTask.cancel();
            ClientContext.baritoneBridge.cancelMining();
            ClientContext.baritoneBridge.cancelPathing();
            this.escapeTarget = null;
            this.state = AutoMiningState.ESCAPING_DEEP_DARK;
            this.notify("EscapingDeepDark");
            return;
        }
        switch (this.state) {
            case IDLE -> this.idle();
            case MINING -> this.mining();
            case REPAIRING_PICKAXE -> this.repairingPickaxe();
            case INVENTORY_FULL -> this.inventoryFull();
            case STORAGE_CHAIN -> this.storageChainTick();
            case PLACING_CRAFTING_TABLE -> this.placingCraftingTable();
            case CRAFTING -> this.crafting();
            case WAITING -> this.waiting();
            case ESCAPING_DEEP_DARK -> this.escapingDeepDark();
        }
    }

    private void idle() {
        if (this.tickCounter % 2 == 0) {
            this.state = AutoMiningState.MINING;
            this.startMining();
        }
    }

    private void mining() {
        if (this.isInventoryFull()) {
            ClientContext.baritoneBridge.cancelMining();
            ClientContext.baritoneBridge.cancelPathing();
            this.notify("InventoryFull");
            this.state = AutoMiningState.INVENTORY_FULL;
            return;
        }
        if (this.modeSetting.getValue() == AutoMiningMode.ANCIENT_DEBRIS
                && this.tickCounter - this.lastPickaxeCheckTick >= 20) {
            this.lastPickaxeCheckTick = this.tickCounter;
            int slot = AutoMiningTools.findLowDurabilityPickaxe(this.pickaxeThresholdSetting.getValue());
            if (slot != -1) {
                this.repairPickaxeSlot = slot;
                ClientContext.baritoneBridge.cancelMining();
                this.notify("PickaxeLow");
                this.state = AutoMiningState.REPAIRING_PICKAXE;
                return;
            }
        }
        if (ContainerInventoryMath.shouldRestartMining(ClientContext.baritoneBridge.isMining(),
                this.tickCounter - this.miningStartedTick)) {
            this.startMining();
        }
    }

    private void startMining() {
        this.miningStartedTick = this.tickCounter;
        this.applyYRange(this.minYSetting.getValue(), this.maxYSetting.getValue(),
                this.modeSetting.getValue().getDisplayName());
        if (this.modeSetting.getValue() == AutoMiningMode.DIAMOND) {
            ClientContext.baritoneBridge.mineBlocks(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        } else {
            ClientContext.baritoneBridge.mineBlocks(Blocks.ANCIENT_DEBRIS);
        }
    }

    private void applyYRange(int minY, int maxY, String label) {
        ClientContext.baritoneBridge.setMiningYRange(minY, maxY);
        if (ContainerInventoryMath.isYRangeChanged(this.appliedYRange, minY, maxY)) {
            this.appliedYRange = minY + "~" + maxY;
            this.notify("YRangeChanged", label, String.valueOf(minY), String.valueOf(maxY));
        }
    }

    /**
     * Pushes Y band changes straight to Baritone so the running mine process rescans with the new
     * limits instead of waiting for the next restart.
     */
    private void onYRangeChanged() {
        if (!this.isToggled() || this.isWorldUnavailable()) {
            return;
        }
        ClientContext.baritoneBridge.setMiningYRange(this.minYSetting.getValue(), this.maxYSetting.getValue());
    }

    private void mineQuartz() {
        this.miningStartedTick = this.tickCounter;
        this.applyYRange(10, 117, Translations.ui("modules.AutoMining.QuartzLabel"));
        ClientContext.baritoneBridge.mineBlocks(Blocks.NETHER_QUARTZ_ORE);
    }

    private void repairingPickaxe() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        ItemStack pickaxe = this.repairPickaxeSlot >= 0
                ? player.getInventory().getItem(this.repairPickaxeSlot)
                : ItemStack.EMPTY;
        if (!AutoMiningTools.isPickaxe(pickaxe)) {
            if (AutoMiningTools.findFortunePickaxeSlot() == -1) {
                this.stopWithMessage("MissingFortunePickaxe");
                return;
            }
            this.finishRepair();
            return;
        }
        if (!ContainerInventoryMath.isDurabilityLow(pickaxe.getMaxDamage(), pickaxe.getDamageValue(),
                this.pickaxeThresholdSetting.getValue())) {
            this.finishRepair();
            return;
        }
        this.dropQuartzJunk();
        this.craftQuartzBlock();
        if (ClientAccess.evernightMinecraft.screen == null
                && !ClientContext.baritoneBridge.isMining()
                && this.tickCounter - this.miningStartedTick >= 20) {
            this.mineQuartz();
        }
    }

    /**
     * Quartz blocks are a by-product of the repair: they pile up quickly, so throw them away (and the raw
     * quartz once the blocks are gone) before the inventory fills up with junk.
     */
    private void dropQuartzJunk() {
        if (!this.actionTimer.hasElapsed(this.craftDelayMillis())) {
            return;
        }
        int quartzBlocks = InventoryUtils.countItems(stack -> stack.is(Items.QUARTZ_BLOCK));
        boolean tooMany = quartzBlocks >= 3 * Items.QUARTZ_BLOCK.getDefaultMaxStackSize();
        if (!tooMany && this.freeMainSlots() > 2) {
            return;
        }
        ContainerInteractionUtils.Dropped dropped = ContainerInteractionUtils.throwStack(Items.QUARTZ_BLOCK, Items.QUARTZ);
        if (dropped != null) {
            this.notify("DroppedStack", dropped.item().getDefaultInstance().getHoverName().getString(),
                    String.valueOf(dropped.count()));
        }
    }

    private int freeMainSlots() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int free = 0;
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    private void finishRepair() {
        ClientContext.baritoneBridge.cancelMining();
        this.repairPickaxeSlot = -1;
        this.quartzScreenPhase = false;
        this.silentQuartzAttempts = 0;
        this.quartzPlacementAttempts = 0;
        this.quartzCraftFailures = 0;
        this.notify("PickaxeRepaired");
        this.state = AutoMiningState.MINING;
        this.startMining();
    }

    private boolean isInventoryFull() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Inventory inventory = player.getInventory();
        int[] counts = new int[ContainerInventoryMath.INVENTORY_SIZE];
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            counts[slot] = inventory.getItem(slot).getCount();
        }
        return ContainerInventoryMath.isMainInventoryFull(counts);
    }

    /**
     * Picks the item the storage chain moves into the shulker box.
     *
     * <p>Crafted blocks are the compressed loot, so they always leave the inventory first: as long as
     * any diamond block is carried it is the storage target, no matter how many raw diamonds are left
     * next to it. Raw diamonds are only stored when nothing can be compressed (no crafting table, or
     * fewer than nine diamonds), because otherwise the inventory would never free a slot.
     */
    private void updateStorageTarget() {
        if (this.modeSetting.getValue() == AutoMiningMode.ANCIENT_DEBRIS) {
            this.storageTarget = Items.ANCIENT_DEBRIS;
            return;
        }
        boolean hasBlocks = InventoryUtils.countItems(stack -> stack.is(Items.DIAMOND_BLOCK)) > 0;
        this.storageTarget = hasBlocks || this.canCompressDiamonds()
                ? Items.DIAMOND_BLOCK
                : Items.DIAMOND;
    }

    /**
     * @return whether a crafting table of this run is carried or already placed in the world
     */
    private boolean hasCraftingTable() {
        return this.pendingTablePos != null || AutoMiningTools.findSlot(Items.CRAFTING_TABLE) != -1;
    }

    /**
     * @return whether at least one more block can be crafted right now; a table without nine diamonds
     * or a recipe book that rejected the recipe leaves only the raw loot to store
     */
    private boolean canCompressDiamonds() {
        return this.craftingAvailable
                && this.hasCraftingTable()
                && InventoryUtils.countItems(stack -> stack.is(Items.DIAMOND)) >= 9;
    }

    // ================= quartz crafting (repair) =================

    private void onModeChanged() {
        this.minYSetting.setValue(this.modeSetting.getValue().getDefaultMinY());
        this.maxYSetting.setValue(this.modeSetting.getValue().getDefaultMaxY());
        this.craftingAvailable = true;
        this.updateStorageTarget();
        if (!this.isToggled()) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null) {
            return;
        }
        boolean nether = player.level().dimension() == Level.NETHER;
        if ((this.modeSetting.getValue() == AutoMiningMode.DIAMOND && nether)
                || (this.modeSetting.getValue() == AutoMiningMode.ANCIENT_DEBRIS && !nether)) {
            this.stopWithMessage("WrongDimension");
            return;
        }
        this.storageChain.cancel();
        this.craftingTask.cancel();
        ClientContext.baritoneBridge.cancelMining();
        ClientContext.baritoneBridge.cancelPathing();
        this.state = AutoMiningState.MINING;
        this.startMining();
    }

    /**
     * Crafts quartz blocks out of mined quartz to keep the inventory from filling up.
     *
     * <p>The silent 2x2 grid of the player inventory menu is tried first; some servers ignore container
     * clicks while no screen is open, so after two attempts the module opens the regular inventory screen
     * and does the same recipe book placement there.
     */
    private void craftQuartzBlock() {
        if (AutoMiningTools.findSlot(Items.CRAFTING_TABLE) == -1) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null || this.quartzCraftFailures >= 3) {
            return;
        }
        if (this.tickCounter < this.quartzCooldownUntilTick) {
            return;
        }
        int quartz = InventoryUtils.countItems(stack -> stack.is(Items.QUARTZ));
        if (quartz < 4) {
            return;
        }
        if (!this.actionTimer.hasElapsed(this.craftDelayMillis())) {
            return;
        }
        Screen screen = ClientAccess.evernightMinecraft.screen;
        AbstractContainerMenu menu = player.containerMenu;
        if (this.quartzScreenPhase) {
            if (!(screen instanceof InventoryScreen) || !(menu instanceof InventoryMenu)) {
                if (screen == null) {
                    ClientContext.baritoneBridge.cancelMining();
                    ClientAccess.evernightMinecraft.setScreen(new InventoryScreen(player));
                    this.actionTimer.reset();
                }
                return;
            }
        } else if (screen != null) {
            return;
        }
        RecipeDisplayEntry recipe = AutoMiningCrafting.findRecipe(Items.QUARTZ_BLOCK);
        if (recipe == null) {
            this.failQuartzCraft();
            return;
        }
        if (this.takeCraftedResult(Items.QUARTZ_BLOCK, Items.QUARTZ_BLOCK, Items.QUARTZ)) {
            if (this.quartzScreenPhase) {
                ContainerInteractionUtils.closeScreen();
                this.quartzScreenPhase = false;
            }
            this.silentQuartzAttempts = 0;
            this.quartzPlacementAttempts = 0;
            return;
        }
        AutoMiningCrafting.placeRecipe(menu, recipe, true);
        this.actionTimer.reset();
        if (!this.quartzScreenPhase && ++this.silentQuartzAttempts >= 2) {
            this.quartzScreenPhase = true;
            this.quartzPlacementAttempts = 0;
            return;
        }
        if (this.quartzPlacementAttempts >= 3) {
            this.failQuartzCraft();
        } else {
            this.quartzPlacementAttempts++;
        }
    }

    // ================= storage chain =================

    private void failQuartzCraft() {
        this.quartzCraftFailures++;
        this.quartzPlacementAttempts = 0;
        this.quartzCooldownUntilTick = this.tickCounter + 100;
        this.debug("QuartzFailDebug", String.valueOf(this.quartzCraftFailures));
        if (this.quartzCraftFailures >= 3) {
            this.notify("QuartzCraftFailed");
            if (this.quartzScreenPhase) {
                ContainerInteractionUtils.closeScreen();
                this.quartzScreenPhase = false;
            }
        }
    }

    private void inventoryFull() {
        if (!this.isInventoryFull()) {
            this.noShulkerPrompted = false;
            this.state = AutoMiningState.MINING;
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
        this.pendingTablePos = null;
        this.updateStorageTarget();
        this.storageChain.beginStore();
        this.state = AutoMiningState.STORAGE_CHAIN;
    }

    /**
     * Delegates one tick to the shared storage chain. The chain handles safe spot search, pocket
     * carving, box/ender chest handling, reclaiming and pickup; this module only reacts to the result.
     */
    private void storageChainTick() {
        StorageChainController.Status status = this.storageChain.tick();
        switch (status) {
            case PREPARING -> {
            }
            case DONE -> {
                this.pendingTablePos = null;
                this.state = AutoMiningState.MINING;
                this.startMining();
            }
            case FAILED -> this.stopWithMessage(this.storageChain.failureKey());
            default -> {
            }
        }
    }

    /**
     * Called by the shared storage chain after the pocket was carved. AutoMining compresses diamonds
     * into blocks first, so it needs a crafting table inside the freshly carved pocket whenever nine
     * diamonds are left; without a table or with less than that the chain simply stores what is there.
     */
    private boolean prepareStorage() {
        this.updateStorageTarget();
        if (!this.canCompressDiamonds()) {
            return true;
        }
        if (AutoMiningTools.findSlot(Items.CRAFTING_TABLE) == -1) {
            // the table of this run already sits in the pocket, there is nothing to place or open
            return true;
        }
        this.state = AutoMiningState.PLACING_CRAFTING_TABLE;
        return false;
    }

    private void placingCraftingTable() {
        if (this.storageChain.getSafeSpot() == null) {
            this.storageChain.resumeStorage();
            this.state = AutoMiningState.STORAGE_CHAIN;
            return;
        }
        if (!this.craftingTask.isActive()) {
            if (!this.storageChain.ensureStandingAtSpot()) {
                return;
            }
            int slot = AutoMiningTools.findSlot(Items.CRAFTING_TABLE);
            if (slot == -1) {
                this.updateStorageTarget();
                this.storageChain.resumeStorage();
                this.state = AutoMiningState.STORAGE_CHAIN;
                return;
            }
            BlockPos target = StoragePlacement.findPlacePosition(this.storageChain.getSafeSpot());
            if (target == null || !this.craftingTask.startPlace(target, slot)) {
                this.stopWithMessage("NoPlaceSpot");
                return;
            }
            this.placedPos = target;
            return;
        }
        switch (this.craftingTask.tick()) {
            case RUNNING, IDLE -> {
                return;
            }
            case FAILED -> {
                this.placedPos = null;
                this.stopWithMessage("NoPlaceSpot");
                return;
            }
            case DONE -> {
            }
        }
        this.waitTicks(AutoMiningState.CRAFTING, 10);
    }

    private void crafting() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Screen screen = ClientAccess.evernightMinecraft.screen;
        if (!(screen instanceof CraftingScreen) || !(player.containerMenu instanceof CraftingMenu)) {
            ContainerInteractionUtils.closeForeignScreen(CraftingScreen.class);
            ContainerInteractionUtils.openContainer(this.placedPos, this.placeRotateSetting.getValue());
            if (++this.openContainerTicks >= ContainerInventoryMath.OPEN_CONTAINER_TIMEOUT_TICKS) {
                this.stopWithMessage("OpenTimeout");
            }
            return;
        }
        this.openContainerTicks = 0;
        if (!this.actionTimer.hasElapsed(this.craftDelayMillis())) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (this.takeCraftedResult(Items.DIAMOND_BLOCK)) {
            this.craftAttempts = 0;
            return;
        }
        int diamonds = InventoryUtils.countItems(stack -> stack.is(Items.DIAMOND));
        if (!this.craftingAvailable || diamonds < 9) {
            this.finishCrafting();
            return;
        }
        RecipeDisplayEntry recipe = AutoMiningCrafting.findRecipe(Items.DIAMOND_BLOCK);
        if (recipe == null) {
            this.craftingAvailable = false;
            this.updateStorageTarget();
            this.notify("CraftingUnavailable");
            this.finishCrafting();
            return;
        }
        AutoMiningCrafting.placeRecipe(menu, recipe, true);
        this.actionTimer.reset();
        if (++this.craftAttempts >= 5) {
            this.craftingAvailable = false;
            this.updateStorageTarget();
            this.notify("CraftingUnavailable");
            this.finishCrafting();
        }
    }

    private void finishCrafting() {
        ContainerInteractionUtils.closeScreen();
        this.craftAttempts = 0;
        this.pendingTablePos = this.placedPos;
        this.placedPos = null;
        this.updateStorageTarget();
        this.storageChain.queueReclaim(this.pendingTablePos);
        this.storageChain.resumeStorage();
        this.state = AutoMiningState.STORAGE_CHAIN;
    }

    // ================= escape =================

    private void escapingDeepDark() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (this.escapeTarget == null) {
            this.escapeTarget = AutoMiningEscape.findEscapeSpot(player.blockPosition());
            if (this.escapeTarget == null) {
                this.notify("EscapeImpossible");
                this.state = AutoMiningState.MINING;
                return;
            }
            ClientContext.baritoneBridge.pathToXZ(this.escapeTarget.getX(), this.escapeTarget.getZ());
            return;
        }
        if (AutoMiningEscape.hasArrived(player.blockPosition(), this.escapeTarget)) {
            ClientContext.baritoneBridge.cancelPathing();
            this.escapeTarget = null;
            this.notify("EscapedDeepDark");
            this.state = AutoMiningState.MINING;
        }
    }

    private void waiting() {
        if (this.stateWaitTicks > 0) {
            this.stateWaitTicks--;
            return;
        }
        this.state = this.resumeState;
    }

    private void waitTicks(AutoMiningState resumeState, int ticks) {
        this.resumeState = resumeState;
        this.stateWaitTicks = ticks;
        this.state = AutoMiningState.WAITING;
    }

    /**
     * Takes the crafted item out of the result slot, freeing a slot first when the inventory is full.
     */
    private boolean takeCraftedResult(Item expected, Item... dropPriority) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.isEmpty() || !result.is(expected)) {
            return false;
        }
        if (!this.canAcceptCrafted(result)) {
            ContainerInteractionUtils.Dropped dropped = ContainerInteractionUtils.throwStack(dropPriority);
            if (dropped == null) {
                return false;
            }
            this.notify("DroppedStack", dropped.item().getDefaultInstance().getHoverName().getString(),
                    String.valueOf(dropped.count()));
        }
        InventoryUtils.clickSlot(menu.containerId, 0, 0, ClickType.QUICK_MOVE);
        this.actionTimer.reset();
        return true;
    }

    /**
     * @return whether the inventory can take the crafted stack: an empty slot, or an existing stack of
     * the same item that still has room. Counting the merge case keeps the module from throwing a whole
     * stack of loot away only to make room for the same item.
     */
    private boolean canAcceptCrafted(ItemStack result) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(stack, result)
                    && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private long craftDelayMillis() {
        return this.craftDelaySetting.getValue().longValue();
    }

    /**
     * Host adapter for the shared storage chain: ore loot, carved pocket and the crafting table
     * preparation are AutoMining specific; everything else lives in the controller.
     */
    private final class StorageHost implements StorageChainController.Host {
        @Override
        public StorageChainController.Setup storageSetup() {
            return StorageChainController.Setup.CARVE_POCKET;
        }

        @Override
        public Predicate<ItemStack> lootFilter() {
            return stack -> stack.is(AutoMiningModule.this.storageTarget);
        }

        @Override
        public int storageMinY() {
            return AutoMiningModule.this.minYSetting.getValue();
        }

        @Override
        public long clickDelayMillis() {
            return AutoMiningModule.this.craftDelayMillis();
        }

        @Override
        public boolean rotatePlacement() {
            return AutoMiningModule.this.placeRotateSetting.getValue();
        }

        @Override
        public boolean canReclaimEnderChest() {
            return AutoMiningTools.findSilkTouchPickaxeSlot() != -1;
        }

        @Override
        public boolean prepareStorage() {
            return AutoMiningModule.this.prepareStorage();
        }

        @Override
        public void notifyKey(String key, Object... args) {
            AutoMiningModule.this.notify(key, args);
        }

        @Override
        public void debugKey(String key, Object... args) {
            AutoMiningModule.this.debug(key, args);
        }
    }

    private void debug(String key, Object... args) {
        if (!this.debugOutputSetting.getValue()) {
            return;
        }
        this.notify(key, args);
    }

    private void notify(String key, Object... args) {
        String message = Translations.format("modules.AutoMining." + key, args);
        ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this));
    }

    /**
     * Prints the reason, then either logs the player out (when enabled) or simply turns the module off.
     */
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
        String reason = Translations.format("modules.AutoMining.EmergencyLogout");
        ClientAccess.evernightMinecraft.getConnection().getConnection().disconnect(Component.literal(reason));
    }
}
