package evernight.client.modules.combat.Mainhand;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.auto.AutoEat.AutoEatModule;
import evernight.client.modules.player.NoInteract.NoInteractModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.core.KeyUtils;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Predicate;
import evernight.client.modules.combat.AutoCrystal.AutoCrystalModule;
import evernight.client.modules.combat.AutoExp.AutoExpModule;
import evernight.client.modules.combat.Offhand.OffhandModule;
import evernight.client.modules.combat.LethalDamageTracker;

public class MainhandModule extends Module implements LethalDamageTracker.LethalCheckPolicy {
    private static NoInteractModule noInteractModule = ClientContext.getModule(NoInteractModule.class);
    private static AutoCrystalModule autoCrystalModule = ClientContext.getModule(AutoCrystalModule.class);
    private static AutoEatModule autoEatModule = ClientContext.getModule(AutoEatModule.class);
    private static AutoExpModule autoExpModule = ClientContext.getModule(AutoExpModule.class);
    private static OffhandModule offhandModule = ClientContext.getModule(OffhandModule.class);
    final Setting<Integer> slotSetting;
    final Setting<Integer> delaySetting;
    private final Setting<Float> safeSetting;
    private final Setting<Boolean> autoSelectSetting;
    private final Setting<Integer> autoSelectDelaySetting;
    private final Setting<Boolean> lethalSetting;
    private final Setting<Float> healthSetting;
    private final Setting<Boolean> gappleBindSetting;
    private final Setting<Boolean> fallbackSetting;
    private final Setting<Boolean> crappleSetting;
    private final Setting<Boolean> hotbarSetting;
    private final Setting<Boolean> weaponSetting;
    private final Setting<Boolean> totemSetting;
    private final ElapsedTimer replacementTimer;
    private final ElapsedTimer autoSelectTimer;
    private final ElapsedTimer interactionCooldownTimer;
    private final LethalDamageTracker lethalDamageTracker;
    private boolean tickReady;
    private int previousReplacementSlot;
    private boolean temporaryHotbarSelection;
    private int previousSelectedHotbarSlot;

    public MainhandModule() {
        super("Mainhand", ModuleCategory.COMBAT);
        this.slotSetting = this.add(new NumberSetting<>("Slot", 1, 1, 9));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms"));
        this.safeSetting = this.add(new NumberSetting<>("Safe", 8.0F, 0.0F, 36.0F).withUnit("hp"));
        Setting<Boolean> autoSelectOption = this.add(new BooleanSetting("AutoSelect", false).markVisibilityDependency());
        this.autoSelectSetting = autoSelectOption;
        this.autoSelectDelaySetting = this.add(new NumberSetting<>("Delay", 25, 0, 500).withUnit("ms").dependsOn(autoSelectOption));
        this.lethalSetting = this.add(new BooleanSetting("Lethal", true).dependsOn(autoSelectOption));
        this.healthSetting = this.add(new NumberSetting<>("Health", 8.0F, 0.0F, 36.0F).withUnit("hp").dependsOn(autoSelectOption));
        Setting<Boolean> gappleBindOption = this.add(new BooleanSetting("GappleBind", true).markVisibilityDependency());
        this.gappleBindSetting = gappleBindOption;
        this.fallbackSetting = this.add(new BooleanSetting("Fallback", true).dependsOn(gappleBindOption));
        this.crappleSetting = this.add(new BooleanSetting("Crapple", false).dependsOn(gappleBindOption));
        Setting<Boolean> hotbarOption = this.add(new BooleanSetting("Hotbar", false).markVisibilityDependency());
        this.hotbarSetting = hotbarOption;
        this.weaponSetting = this.add(new BooleanSetting("Weapon", false).dependsOn(hotbarOption));
        this.totemSetting = this.add(new BooleanSetting("Totem", false).dependsOn(hotbarOption));
        this.replacementTimer = new ElapsedTimer();
        this.autoSelectTimer = new ElapsedTimer();
        this.interactionCooldownTimer = new ElapsedTimer();
        this.lethalDamageTracker = new LethalDamageTracker(this);
        this.autoSelectDelaySetting.setConfigName("SwapDelay");
    }

    public static void bindModuleConnections(ModuleRegistry registry) {
        noInteractModule = registry.getModule(NoInteractModule.class);
        autoCrystalModule = registry.getModule(AutoCrystalModule.class);
        autoEatModule = registry.getModule(AutoEatModule.class);
        autoExpModule = registry.getModule(AutoExpModule.class);
        offhandModule = registry.getModule(OffhandModule.class);
    }

    @Override
    public String getInfo() {
        int itemCount = ClientAccess.evernightMinecraft.player.getInventory().getNonEquipmentItems().stream().filter(stack -> {
            Item stackItem = stack.getItem();
            return stackItem == getDesiredItem();
        }).mapToInt(ItemStack::getCount).sum();
        return String.valueOf(itemCount);
    }

    @Override
    public void onEnable() {
        this.previousSelectedHotbarSlot = this.previousReplacementSlot = -1;
    }

    private boolean shouldTemporarilySelectGapple() {
        ItemStack mainhandStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        if (offhandModule.isToggled()) {
            if (offhandModule.isGappleBindSafeInBlocks()) {
                OffhandModule bindOwner = offhandModule;
                Item heldItem = mainhandStack.getItem();
                if (bindOwner.canUseGappleBind(heldItem)) {
                    return false;
                }
            }
        }
        if (!canUseGappleBind()) {
            return false;
        }
        TagKey swords = ItemTags.SWORDS;
        if (mainhandStack.is(swords) || mainhandStack.is(Items.MACE)) {
            if (this.weaponSetting.getValue()) {
                return true;
            }
        }
        Item totem = Items.TOTEM_OF_UNDYING;
        if (mainhandStack.is(totem)) {
            if (this.totemSetting.getValue()) {
                return true;
            }
        }
        return this.temporaryHotbarSelection;
    }

    public int getConfiguredHotbarSlot() {
        return this.slotSetting.getValue() - 1;
    }

    private boolean canUseGappleBind() {
        HitResult hitResult = ClientAccess.evernightMinecraft.hitResult;
        if (WorldUtils.isInteractive(hitResult)) {
            if (!ClientAccess.evernightMinecraft.player.isShiftKeyDown()) {
                if (!noInteractModule.isToggled()) {
                    if (!ClientAccess.evernightMinecraft.player.isUsingItem()) return false;
                    if (ClientAccess.evernightMinecraft.player.getUsedItemHand() != InteractionHand.MAIN_HAND)
                        return false;
                    if (!ItemUtils.isFood(ClientAccess.evernightMinecraft.player.getMainHandItem())) return false;
                }
            }
        }
        KeyMapping useKey = ClientAccess.evernightMinecraft.options.keyUse;
        if (!useKey.isDown()) return false;
        float health = EntityUtils.getPlayerHealthWithAbsorption();
        Float safeHealth = this.safeSetting.getValue();
        return health >= safeHealth;
    }

    private void autoSelectTotem() {
        if (this.autoSelectSetting.getValue()) {
            ItemStack configuredStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(getConfiguredHotbarSlot());
            Item configuredItem = configuredStack.getItem();
            if (configuredItem == Items.TOTEM_OF_UNDYING) {
                if (ClientAccess.evernightMinecraft.player.isUsingItem()) {
                    if (ClientAccess.evernightMinecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                        return;
                    }
                }
                if (!this.lethalDamageTracker.shouldHoldTotem()) {
                    float health = EntityUtils.getPlayerHealthWithAbsorption();
                    Float requiredHealth = this.healthSetting.getValue();
                    if (!(health <= requiredHealth)) {
                        return;
                    }
                }
                ElapsedTimer selectTimer = this.autoSelectTimer;
                long delayMillis = (this.autoSelectDelaySetting.getValue()).intValue();
                if (selectTimer.hasElapsedAndReset(delayMillis)) {
                    InventoryUtils.selectHotbarSlotForPlacement(getConfiguredHotbarSlot());
                }
            }
        }
    }

    private Item getGappleItem() {
        return this.crappleSetting.getValue() ? Items.GOLDEN_APPLE : Items.ENCHANTED_GOLDEN_APPLE;
    }

    private Item getDesiredItem() {
        int selectedSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        if (selectedSlot == getConfiguredHotbarSlot()) {
            if (this.gappleBindSetting.getValue()) {
                if (canStartGappleBind()) {
                    if (canUseGappleBind()) {
                        return getGappleItem();
                    }
                }
            }
        }
        if (this.gappleBindSetting.getValue()) {
            if (this.fallbackSetting.getValue()) {
                if (!hasTotemAvailable()) {
                    return getGappleItem();
                }
            }
        }
        return Items.TOTEM_OF_UNDYING;
    }

    private boolean hasTotemAvailable() {
        ItemStack configuredStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(getConfiguredHotbarSlot());
        Item totem = Items.TOTEM_OF_UNDYING;
        if (configuredStack.is(totem)) {
            return true;
        }
        int totemSlot = InventoryUtils.findInventorySlot(stack -> {
            Item searchedTotem = Items.TOTEM_OF_UNDYING;
            return stack.is(searchedTotem);
        }, true);
        return totemSlot != -1;
    }

    private void selectGappleTemporarily() {
        if (!ClientAccess.evernightMinecraft.player.isUsingItem()) {
            if (!ClientAccess.evernightMinecraft.player.getMainHandItem().is(getGappleItem())) {
                int gappleSlot = InventoryUtils.findHotbarSlot(getGappleItem());
                this.previousSelectedHotbarSlot = InventoryUtils.getSelectedSlot();
                InventoryUtils.selectHotbarSlotForPlacement(gappleSlot);
                this.temporaryHotbarSelection = true;
            }
        }
    }

    private boolean canStartGappleBind() {
        ItemStack mainhandStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        if (ItemUtils.isFood(ClientAccess.evernightMinecraft.player.getOffhandItem())) {
            return false;
        }
        Item totem = Items.TOTEM_OF_UNDYING;
        if (!mainhandStack.is(totem)) {
            return mainhandStack.is(getGappleItem());
        }
        return true;
    }

    @EventHandler(priority = 100)
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        if (this.isWorldUnavailable()) {
            return;
        }
        this.tickReady = event.hasPendingTicks();
        this.lethalDamageTracker.update();
        if (ClientAccess.evernightMinecraft.player.containerMenu != ClientAccess.evernightMinecraft.player.inventoryMenu) {
            return;
        }
        if (autoCrystalModule.isToggled()) {
            if (autoCrystalModule.forceSuicideSetting.getValue()) {
                return;
            }
        }
        if (autoEatModule.isToggled()) {
            if (autoEatModule.isEating()) {
                return;
            }
        }
        if (autoExpModule.isToggled()) {
            if (autoExpModule.isActive()) {
                return;
            }
        }
        if (this.hotbarSetting.getValue() && this.tickReady) {
            if (shouldTemporarilySelectGapple()) {
                selectGappleTemporarily();
                return;
            }
            if (this.temporaryHotbarSelection) {
                InventoryUtils.selectHotbarSlotForPlacement(this.previousSelectedHotbarSlot);
                this.temporaryHotbarSelection = false;
                return;
            }
        }
        if (this.tickReady) {
            autoSelectTotem();
        }
        Item desiredItem = getDesiredItem();
        ItemStack configuredStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(getConfiguredHotbarSlot());
        if (desiredItem != configuredStack.getItem()) {
            replaceConfiguredSlot(desiredItem);
        }
    }

    @EventHandler
    private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
        BlockHitResult hitResult = event.getHitResult();
        if (!WorldUtils.isInteractive(hitResult)) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player.isShiftKeyDown()) {
            return;
        }
        if (!noInteractModule.isToggled() && !event.isCancelled()) {
            this.interactionCooldownTimer.reset();
        }
    }

    private void replaceConfiguredSlot(Item desiredItem) {
        int replacementSlot = InventoryUtils.findInventorySlot(stack -> {
            return stack.is(desiredItem);
        }, true);
        if (replacementSlot < 9) {
            replacementSlot = InventoryUtils.toMenuSlot(replacementSlot);
        }
        if (this.previousReplacementSlot != -1) {
            ItemStack previousStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(this.previousReplacementSlot);
            if (previousStack.is(desiredItem) && replacementSlot != -1) {
                replacementSlot = this.previousReplacementSlot;
            }
        }
        if (replacementSlot != -1) {
            NonNullList menuSlots = ClientAccess.evernightMinecraft.player.containerMenu.slots;
            if (replacementSlot < menuSlots.size()) {
                AbstractContainerMenu menu = ClientAccess.evernightMinecraft.player.containerMenu;
                Slot slot = menu.getSlot(replacementSlot);
                ItemStack replacementStack = slot.getItem();
                if (!replacementStack.has(DataComponents.FOOD) || this.tickReady) {
                    ElapsedTimer swapTimer = this.replacementTimer;
                    long delayMillis = (this.delaySetting.getValue()).intValue();
                    if (swapTimer.hasElapsedAndReset(delayMillis)) {
                        if (this.interactionCooldownTimer.hasElapsed(200L)) {
                            int configuredSlot = getConfiguredHotbarSlot();
                            InventoryUtils.swapWithHotbarSlot(replacementSlot, configuredSlot);
                            ClientContext.inventoryStateManager.close();
                            this.previousReplacementSlot = replacementSlot;
                        }
                    }
                    KeyMapping useKey = ClientAccess.evernightMinecraft.options.keyUse;
                    if (KeyUtils.isPhysicallyDown(useKey) && replacementStack.has(DataComponents.FOOD)) {
                        if (ClientAccess.evernightMinecraft.player.isUsingItem()) {
                            if (ClientAccess.evernightMinecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                                ClientAccess.evernightMinecraft.player.releaseUsingItem();
                            }
                        }
                        MultiPlayerGameMode gameModeForUse = ClientAccess.evernightMinecraft.gameMode;
                        LocalPlayer playerForUse = ClientAccess.evernightMinecraft.player;
                        InteractionHand offhand = InteractionHand.OFF_HAND;
                        gameModeForUse.useItem(playerForUse, offhand);
                    }
                }
            }
        }
    }

    @Override
    public boolean isLethalCheckEnabled() {
        if (!this.lethalSetting.getValue()) {
            return false;
        }
        return this.autoSelectSetting.getValue();
    }
}