package evernight.client.modules.player.AutoTool;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.MultiPlayerGameModeStartDestroyBlockEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.settings.options.OreEnchantmentChoice;
import evernight.client.utils.inventory.EnchantmentUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockBreakingState;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import evernight.client.modules.player.Nuker.NukerModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class AutoToolModule extends Module {
    private static final List<TagKey<net.minecraft.world.level.block.Block>> ORE_TAGS = List.of(
            BlockTags.COAL_ORES,
            BlockTags.IRON_ORES,
            BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES,
            BlockTags.REDSTONE_ORES,
            BlockTags.LAPIS_ORES,
            BlockTags.DIAMOND_ORES,
            BlockTags.EMERALD_ORES);
    private static final int SESSION_GRACE_TICKS = 10;

    private static AntiCheatModule antiCheatModule;
    private static NukerModule nukerModule;

    static {
        antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
        nukerModule = ClientContext.getModule(NukerModule.class);
    }

    public final Setting<Float> blockDamageSetting;
    public final Setting<Boolean> noBreakDelaySetting;
    private final Setting<Boolean> inventorySetting;
    private final Setting<Boolean> swapBackSetting;
    private final Setting<OreEnchantmentChoice> oreEnchantmentSetting;
    private final Setting<Boolean> enderChestSilkSetting;
    private final Setting<Boolean> enchantmentPrioritySetting;
    private int originalHotbarSlot = -1;
    private int miningIdleTicks;
    private boolean miningSession;
    private boolean sessionFromAttack;
    private int borrowedInventorySlot = -1;
    private int borrowedHotbarSlot = -1;
    private Item borrowedItem;

    public AutoToolModule() {
        super("AutoTool", ModuleCategory.PLAYER);
        this.blockDamageSetting = this.add(new NumberSetting<>("BlockDamage", 1.0F, 0.7F, 1.0F));
        this.inventorySetting = this.add(new BooleanSetting("Inventory", true));
        this.swapBackSetting = this.add(new BooleanSetting("SwapBack", false));
        this.noBreakDelaySetting = this.add(new BooleanSetting("NoBreakDelay", false));
        BooleanSetting enchantmentPriority = new BooleanSetting("EnchantmentPriority", true);
        enchantmentPriority.markAsGroup();
        enchantmentPriority.markVisibilityDependency();
        this.enchantmentPrioritySetting = this.add(enchantmentPriority);
        EnumSetting<OreEnchantmentChoice> oreEnchantment =
                new EnumSetting<>("OreEnchantment", OreEnchantmentChoice.FORTUNE);
        oreEnchantment.setConfigName("OreFortune");
        this.oreEnchantmentSetting = this.add(oreEnchantment.dependsOn(this.enchantmentPrioritySetting));
        this.enderChestSilkSetting = this.add(new BooleanSetting("EnderChestSilk", true)
                .dependsOn(this.enchantmentPrioritySetting));
        Setting<Float> vanillaBlockDamageSetting = this.blockDamageSetting;
        SettingLabelCondition maximumValueMarker = SettingLabelCondition.MAX;
        vanillaBlockDamageSetting.setBoundaryLabel("Vanilla", maximumValueMarker);
    }

    public static void bindModules(ModuleRegistry moduleRegistry) {
        antiCheatModule = moduleRegistry.getModule(AntiCheatModule.class);
        nukerModule = moduleRegistry.getModule(NukerModule.class);
    }

    /**
     * @return whether the block belongs to a vanilla ore tag; ancient debris and nether quartz are not
     * tagged as ore, so they are checked explicitly
     */
    private static boolean isOre(BlockState state) {
        if (state.is(Blocks.ANCIENT_DEBRIS) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
            return true;
        }
        for (TagKey<net.minecraft.world.level.block.Block> tag : ORE_TAGS) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlockDamageOverrideActive() {
        if (!this.isToggled()) {
            return false;
        }
        Setting<Float> blockDamage = this.blockDamageSetting;
        return !blockDamage.isAtMaximum();
    }

    @EventHandler
    private void onMultiPlayerGameModeStartDestroyBlockEvent(MultiPlayerGameModeStartDestroyBlockEvent event) {
        if (this.isNukerMining(event.getPos())) {
            return;
        }
        BlockPos blockPos = event.getPos();
        this.miningIdleTicks = 0;
        this.beginMiningSession(ClientAccess.evernightMinecraft.options.keyAttack.isDown());
        this.equipBestTool(blockPos);
        if (!this.isBlockDamageOverrideActive()) {
            return;
        }
        Direction direction = event.getDirection();
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        BlockState blockState = level.getBlockState(blockPos);
        ItemStack heldItem = ClientAccess.evernightMinecraft.player.getMainHandItem();
        boolean onGround = ClientAccess.evernightMinecraft.player.onGround();
        double blockDamage = WorldUtils.getBreakProgress(heldItem, blockState, onGround);
        Setting<Float> blockDamageSetting = this.blockDamageSetting;
        Float threshold = blockDamageSetting.getValue();
        if (blockDamage < threshold.floatValue()) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!WorldUtils.canMineBlock(blockPos)) {
            return;
        }
        Action startAction = Action.START_DESTROY_BLOCK;
        PacketSender.sendPlayerAction(startAction, blockPos, direction);
        Action stopAction = Action.STOP_DESTROY_BLOCK;
        PacketSender.sendPlayerAction(stopAction, blockPos, direction);
        if (antiCheatModule.is2b2tEnabled()) {
            Action abortAction = Action.ABORT_DESTROY_BLOCK;
            PacketSender.sendPlayerAction(abortAction, blockPos, direction);
        }
        ClientContext.worldStateManager.destroyBlockPredicted(blockPos);
        event.cancel();
    }

    @EventHandler
    private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent tickEvent) {
        if (ClientAccess.evernightMinecraft.player == null || ClientAccess.evernightMinecraft.level == null
                || ClientAccess.evernightMinecraft.gameMode == null
                || ClientAccess.evernightMinecraft.screen != null) {
            return;
        }
        BlockBreakingState breakingState = (BlockBreakingState) ClientAccess.evernightMinecraft.gameMode;
        BlockPos breakingPos = breakingState.isBreakingBlock() ? breakingState.getCurrentBreakingBlock() : null;
        boolean attacking = ClientAccess.evernightMinecraft.options.keyAttack.isDown();
        if (breakingPos != null) {
            if (this.miningSession) {
                this.miningIdleTicks = 0;
            }
            if (attacking && !this.isNukerMining(breakingPos)) {
                this.beginMiningSession(true);
                this.equipBestTool(breakingPos);
            }
            return;
        }
        HitResult hitResult = ClientAccess.evernightMinecraft.hitResult;
        if (attacking && hitResult instanceof BlockHitResult blockHit
                && !this.isNukerMining(blockHit.getBlockPos())) {
            this.miningIdleTicks = 0;
            this.beginMiningSession(true);
            this.equipBestTool(blockHit.getBlockPos());
            return;
        }
        if (attacking) {
            // The attack key is held but no block is targeted any more, so mining has stopped.
            this.endMiningSession();
            return;
        }
        if (!this.miningSession) {
            return;
        }
        // Automated breaks (Baritone and friends) pause between blocks: keep the tool for a moment
        // before giving it back.
        if (this.sessionFromAttack || ++this.miningIdleTicks >= SESSION_GRACE_TICKS) {
            this.endMiningSession();
        }
    }

    /**
     * Starts a mining session and remembers the hotbar slot to return to. A session lives while blocks
     * keep being broken, so continuous mining does not churn the hotbar.
     *
     * @param fromAttack whether the player is holding the attack key, which ends the session as soon as
     *                   mining stops instead of waiting for the automated-break grace period
     */
    private void beginMiningSession(boolean fromAttack) {
        if (this.miningSession) {
            this.sessionFromAttack |= fromAttack;
            return;
        }
        this.miningSession = true;
        this.sessionFromAttack = fromAttack;
        this.miningIdleTicks = 0;
        this.originalHotbarSlot = InventoryUtils.getSelectedSlot();
    }

    /**
     * Equips the best tool for the block. A hotbar tool is selected in place; with {@code Inventory} on
     * a tool from the main inventory is swapped into the selected hotbar slot instead.
     * {@link #endMiningSession()} puts it back when {@code SwapBack} is on.
     */
    private void equipBestTool(BlockPos position) {
        if (ClientAccess.evernightMinecraft.player == null || ClientAccess.evernightMinecraft.gameMode == null
                || ClientAccess.evernightMinecraft.screen != null) {
            return;
        }
        if (this.borrowedInventorySlot != -1 && !this.isBorrowIntact()) {
            // The player moved a stack in the meantime; the borrow cannot be undone cleanly.
            this.clearBorrow();
        }
        boolean searchInventory = this.inventorySetting.getValue();
        int bestToolSlot = this.pickToolSlot(position, !searchInventory);
        if (bestToolSlot < 0) {
            return;
        }
        if (bestToolSlot < Inventory.SELECTION_SIZE) {
            if (this.borrowedInventorySlot != -1 && bestToolSlot != this.borrowedHotbarSlot) {
                // A hotbar tool beats the borrowed one: hand the borrowed stack back first and select
                // the better slot on the next tick.
                this.restoreBorrow();
                return;
            }
            if (InventoryUtils.getSelectedSlot() != bestToolSlot) {
                InventoryUtils.selectHotbarSlot(bestToolSlot);
            }
            return;
        }
        if (!searchInventory) {
            return;
        }
        if (this.borrowedInventorySlot != -1) {
            // Another inventory tool beats the borrowed one: give the borrowed stack back first and
            // borrow the better tool on the next tick.
            this.restoreBorrow();
            return;
        }
        this.borrowTool(bestToolSlot);
    }

    /**
     * Swaps a main inventory stack into the selected hotbar slot and remembers where it came from so
     * the swap can be undone. The stack that was held is left in the inventory slot until then.
     */
    private void borrowTool(int inventorySlot) {
        Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
        ItemStack toolStack = inventory.getItem(inventorySlot);
        if (toolStack.isEmpty()) {
            return;
        }
        int hotbarSlot = inventory.getSelectedSlot();
        Item toolItem = toolStack.getItem();
        InventoryUtils.swapIntoSelectedSlot(inventorySlot);
        this.borrowedInventorySlot = inventorySlot;
        this.borrowedHotbarSlot = hotbarSlot;
        this.borrowedItem = toolItem;
    }

    /**
     * Puts the borrowed stack back into the inventory slot it came from and the stack it replaced back
     * into the hotbar. Does nothing when the player moved either stack in the meantime.
     */
    private void restoreBorrow() {
        if (this.borrowedInventorySlot == -1) {
            return;
        }
        if (this.isBorrowIntact()) {
            InventoryUtils.swapWithHotbarSlot(
                    InventoryUtils.toMenuSlot(this.borrowedInventorySlot), this.borrowedHotbarSlot);
        }
        this.clearBorrow();
    }

    /**
     * @return whether the borrowed stack still sits in the hotbar slot it was swapped into. Only that
     * side is checked: the emptied inventory slot can legitimately receive mining drops, and
     * refusing to swap back in that case would leave the borrowed tool in the hand.
     */
    private boolean isBorrowIntact() {
        if (this.borrowedInventorySlot == -1) {
            return false;
        }
        Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
        return inventory.getItem(this.borrowedHotbarSlot).getItem() == this.borrowedItem;
    }

    private void clearBorrow() {
        this.borrowedInventorySlot = -1;
        this.borrowedHotbarSlot = -1;
        this.borrowedItem = null;
    }

    /**
     * Runs once mining stops. With {@code SwapBack} on the borrowed stack and the original hotbar slot
     * are restored; without it the tool stays in the hand for the next break.
     */
    private void endMiningSession() {
        if (!this.miningSession) {
            return;
        }
        this.miningSession = false;
        this.sessionFromAttack = false;
        this.miningIdleTicks = 0;
        boolean canTouchInventory = ClientAccess.evernightMinecraft.player != null
                && ClientAccess.evernightMinecraft.gameMode != null
                && ClientAccess.evernightMinecraft.screen == null;
        if (this.swapBackSetting.getValue() && canTouchInventory) {
            this.restoreBorrow();
            int slot = this.originalHotbarSlot;
            if (slot != -1) {
                InventoryUtils.selectHotbarSlot(slot);
            }
        } else {
            this.clearBorrow();
        }
        this.originalHotbarSlot = -1;
    }

    @Override
    public void onDisable() {
        this.endMiningSession();
        this.miningSession = false;
        this.sessionFromAttack = false;
        this.miningIdleTicks = 0;
        this.originalHotbarSlot = -1;
        this.clearBorrow();
    }

    private boolean isNukerMining(BlockPos position) {
        return nukerModule.isToggled() && nukerModule.canMineBlock(position);
    }

    /**
     * Picks the tool for a block, honouring the enchantment options: an ender chest is mined with silk
     * touch so the chest itself survives, ore is mined with the configured ore enchantment (fortune for
     * extra drops, silk touch to keep the ore block). Everything falls back to the plain fastest-tool
     * pick when the wanted pickaxe is not carried.
     */
    private int pickToolSlot(BlockPos position, boolean hotbarOnly) {
        if (ClientAccess.evernightMinecraft.level == null) {
            return -1;
        }
        if (this.enchantmentPrioritySetting.getValue()) {
            BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(position);
            if (this.enderChestSilkSetting.getValue() && state.is(Blocks.ENDER_CHEST)) {
                int silkTouchSlot = this.findEnchantedPickaxe(Enchantments.SILK_TOUCH, hotbarOnly);
                if (silkTouchSlot != -1) {
                    return silkTouchSlot;
                }
            }
            if (isOre(state)) {
                int orePickaxeSlot = this.findOrePickaxe(hotbarOnly);
                if (orePickaxeSlot != -1) {
                    return orePickaxeSlot;
                }
            }
        }
        return InventoryUtils.findBestToolSlot(position, !hotbarOnly);
    }

    /**
     * Picks the pickaxe for ore: the configured enchantment first, the other one as a fallback so a
     * missing pickaxe never stops the module from mining.
     *
     * @return the slot of the chosen pickaxe, or -1 when neither enchantment is carried
     */
    private int findOrePickaxe(boolean hotbarOnly) {
        boolean silkFirst = this.oreEnchantmentSetting.getValue() == OreEnchantmentChoice.SILK_TOUCH;
        ResourceKey<Enchantment> preferred = silkFirst ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE;
        int preferredSlot = this.findEnchantedPickaxe(preferred, hotbarOnly);
        if (preferredSlot != -1) {
            return preferredSlot;
        }
        ResourceKey<Enchantment> fallback = silkFirst ? Enchantments.FORTUNE : Enchantments.SILK_TOUCH;
        return this.findEnchantedPickaxe(fallback, hotbarOnly);
    }

    /**
     * @return the first slot holding a pickaxe with the enchantment, or -1. Only real inventory slots
     * (hotbar plus main inventory) are searched: armour and offhand indices share the same
     * container layout numbers but map to different menu slots, so swapping them would move the
     * wrong hotbar stack.
     */
    private int findEnchantedPickaxe(ResourceKey<Enchantment> enchantment, boolean hotbarOnly) {
        Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
        int slotCount = hotbarOnly ? Inventory.SELECTION_SIZE : Inventory.INVENTORY_SIZE;
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) {
                continue;
            }
            if (EnchantmentUtils.getLevel(enchantment, stack) > 0) {
                return slot;
            }
        }
        return -1;
    }
}