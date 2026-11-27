package evernight.client.modules.auto.AutoEat;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.network.MultiPlayerGameModeReleaseUsingItemEvent;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.options.AutoEscapeMode;
import evernight.client.settings.options.ListFilterMode;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.inventory.CustomItemRegistries;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.SuspiciousStewEffects.Entry;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.Set;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;

public class AutoEatModule extends Module {
    private final Setting<Set<Item>> itemsSetting;
    private final Setting<ListFilterMode> modeSetting;
    private final Setting<Integer> healthSetting;
    private final Setting<Integer> hungerSetting;
    private final Setting<Boolean> untilFullSetting;
    private final Setting<Boolean> guiOnlySetting;
    private final Setting<Boolean> pauseBaritoneSetting;
    private final Setting<Boolean> autoSwapSetting;
    private final Setting<SortChoice> sortSetting;
    private final Setting<Boolean> swapBackSetting;
    private final Setting<Boolean> autoEscapeSetting;
    private final Setting<AutoEscapeMode> escapeModeSetting;
    private final Setting<Boolean> autoDisableSetting;
    private final ElapsedTimer escapeCooldownTimer = new ElapsedTimer();
    private int previousSelectedSlot;
    private boolean eating;
    private boolean continueUntilFull;
    private boolean allowRelease;
    private int swappedContainerSlot;

    public AutoEatModule() {
        super("AutoEat", ModuleCategory.AUTO);
        DefaultedRegistry itemRegistry = CustomItemRegistries.foodItems;
        Item[] defaultItems = new Item[]{Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE};
        this.itemsSetting = this.add(new RegistrySetSetting<>("Items", itemRegistry, defaultItems));
        this.modeSetting = this.add(new EnumSetting<>("Mode", ListFilterMode.ANY));
        NumberSetting<Integer> health = new NumberSetting<>("Health", 16, 0, 36);
        SettingLabelCondition healthOffBound = SettingLabelCondition.MIN;
        this.healthSetting = this.add(health.setBoundaryLabel("Off", healthOffBound));
        NumberSetting<Integer> hunger = new NumberSetting<>("Hunger", 6, -1, 20);
        SettingLabelCondition hungerOffBound = SettingLabelCondition.MIN;
        this.hungerSetting = this.add(hunger.setBoundaryLabel("Off", hungerOffBound));
        this.untilFullSetting = this.add(new BooleanSetting("UntilFull", false));
        this.guiOnlySetting = this.add(new BooleanSetting("GuiOnly", false));
        this.pauseBaritoneSetting = this.add(new BooleanSetting("PauseBaritone", false));
        this.autoSwapSetting = this.add(new BooleanSetting("AutoSwap", true).markVisibilityDependency());
        AutoEatModule.SortChoice defaultSort = AutoEatModule.SortChoice.SATURATION;
        this.sortSetting = this.add(new EnumSetting<>("Sort", defaultSort).dependsOn(this.autoSwapSetting));
        this.swapBackSetting = this.add(new BooleanSetting("SwapBack", false).dependsOn(this.autoSwapSetting));
        this.autoEscapeSetting = this.add(new BooleanSetting("AutoEscape", false).markVisibilityDependency());
        this.escapeModeSetting = this.add(new EnumSetting<>("EscapeMode", AutoEscapeMode.JOIN_ONLY)
                .dependsOn(this.autoEscapeSetting));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false).dependsOn(this.autoEscapeSetting));
        this.untilFullSetting.addVisibilityPredicate(ignored -> !this.hungerSetting.isAtMinimum());
        this.pauseBaritoneSetting.hideWithoutBaritone();
    }

    @Override
    public void onToggle() {
        this.setEating(false);
        this.previousSelectedSlot = -1;
        this.swappedContainerSlot = -1;
    }

    @Override
    public void onDisable() {
        ClientAccess.evernightMinecraft.options.keyUse.setDown(false);
        ClientContext.baritoneBridge.releasePause(this);
        this.allowRelease = false;
    }

    /**
     * Keeps the item use alive while the module wants to eat.
     *
     * <p>Since 1.21.9 the vanilla key handling drops the use state on the first tick the use key is
     * not held, and that code only runs while no screen is open. That made the module eat only with
     * an inventory screen open, so external releases are cancelled until the module stops eating.
     */
    @EventHandler
    private void onReleaseUsingItem(MultiPlayerGameModeReleaseUsingItemEvent event) {
        if (!this.isToggled() || !this.eating || this.allowRelease) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player == null || !this.canEat(player.getUseItem())) {
            return;
        }
        event.cancel();
    }

    private void releaseUseItem(LocalPlayer player) {
        this.allowRelease = true;
        try {
            ClientAccess.evernightMinecraft.gameMode.releaseUsingItem(player);
        } finally {
            this.allowRelease = false;
        }
    }

    private void stopEating() {
        if (!this.eating) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player != null) {
            this.releaseUseItem(player);
        }
        this.setEating(false);
    }

    private void swapWithSelectedHotbarSlot(int containerSlot) {
        MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
        int containerId = ClientAccess.evernightMinecraft.player.containerMenu.containerId;
        int selectedHotbarSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        ClickType clickType = ClickType.SWAP;
        LocalPlayer clickPlayer = ClientAccess.evernightMinecraft.player;
        gameMode.handleInventoryMouseClick(containerId, containerSlot, selectedHotbarSlot, clickType, clickPlayer);
    }

    @EventHandler
    private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
        EventStage postStage = EventStage.Post;
        if (event.getStage() == postStage) {
            return;
        }
        if (this.guiOnlySetting.getValue() && ClientAccess.evernightMinecraft.screen == null) {
            this.stopEating();
            return;
        }
        if (this.continueUntilFull) {
            FoodData foodData = ClientAccess.evernightMinecraft.player.getFoodData();
            if (!foodData.needsFood()) {
                this.continueUntilFull = false;
            }
        }
        ClientContext.baritoneBridge.releasePause(this);
        Boolean initialAutoSwapEnabled = this.autoSwapSetting.getValue();
        if (!initialAutoSwapEnabled && this.eating) {
            ItemStack heldMainHandStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
            if (!this.canEat(heldMainHandStack)) {
                this.releaseUseItem(ClientAccess.evernightMinecraft.player);
                this.setEating(false);
                return;
            }
        }
        int foodSlot;
        escapeFoodSelection:
        {
            foodSlot = this.findFoodSlot();
            if (foodSlot != -1) {
                ItemStack candidateStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(foodSlot);
                Item chorusFruitToCompare = Items.CHORUS_FRUIT;
                if (candidateStack.is(chorusFruitToCompare)) {
                    break escapeFoodSelection;
                }
            }
            if (this.shouldEscape()) {
                Item chorusFruitToFind = Items.CHORUS_FRUIT;
                int chorusContainerSlot = InventoryUtils.findMenuSlot(chorusFruitToFind);
                if (chorusContainerSlot != -1) {
                    foodSlot = -1;
                    this.swappedContainerSlot = chorusContainerSlot;
                    int escapeContainerSlot = this.swappedContainerSlot;
                    this.swapWithSelectedHotbarSlot(escapeContainerSlot);
                }
            }
        }
        if (this.shouldStopEating()) {
            if (!this.shouldEscape()) {
                boolean useTicksAtThreshold = ClientAccess.evernightMinecraft.player.getTicksUsingItem() >= (2);
                if (this.eating && !useTicksAtThreshold) {
                    this.releaseUseItem(ClientAccess.evernightMinecraft.player);
                    this.setEating(false);
                    if (this.autoSwapSetting.getValue()) {
                        if ((this.swapBackSetting.getValue()) && this.previousSelectedSlot != -1) {
                            int previousHotbarSlot = this.previousSelectedSlot;
                            InventoryUtils.selectHotbarSlotForPlacement(previousHotbarSlot);
                            this.previousSelectedSlot = -1;
                        }
                    }
                    if (this.swappedContainerSlot != -1) {
                        int restoredContainerSlot = this.swappedContainerSlot;
                        this.swapWithSelectedHotbarSlot(restoredContainerSlot);
                        this.swappedContainerSlot = -1;
                    }
                    if (this.autoDisableSetting.getValue()) {
                        if (this.autoEscapeSetting.getValue()) {
                            this.disable();
                        }
                    }
                }
                return;
            }
        }
        boolean useOffHand = foodSlot == (40);
        if ((this.autoSwapSetting.getValue()) && !useOffHand && foodSlot != -1) {
            if (ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot() != foodSlot) {
                this.previousSelectedSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
                InventoryUtils.selectHotbarSlotForPlacement(foodSlot);
            }
        }
        ItemStack mainHandStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        LocalPlayer useStatePlayer = ClientAccess.evernightMinecraft.player;
        if (!useStatePlayer.isUsingItem() && (useOffHand || this.canEat(mainHandStack))) {
            MultiPlayerGameMode useItemGameMode = ClientAccess.evernightMinecraft.gameMode;
            LocalPlayer useItemPlayer = ClientAccess.evernightMinecraft.player;
            InteractionHand useHand = useOffHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            useItemGameMode.useItem(useItemPlayer, useHand);
            this.setEating(true);
        }
        if (this.pauseBaritoneSetting.getValue()) {
            if (ClientAccess.evernightMinecraft.player.isUsingItem()) {
                ClientContext.baritoneBridge.requestPause(this);
            }
        }
    }

    public boolean isEating() {
        return this.eating;
    }

    private void setEating(boolean requestedEating) {
        if (this.eating == requestedEating) {
            return;
        }
        if (requestedEating && this.isUntilFullEnabled()) {
            if (ClientAccess.evernightMinecraft.player.getFoodData().needsFood()) {
                this.continueUntilFull = true;
            }
        }
        this.eating = requestedEating;
    }

    private int findFoodSlot() {
        double bestScore = -1.0D;
        int selectedFoodSlot = -1;
        for (int scanSlot = 0; ; scanSlot++) {
            if (scanSlot >= (10)) {
                break;
            }
            if (scanSlot == (9)) {
                scanSlot = 40;
            }
            ItemStack stack = ClientAccess.evernightMinecraft.player.getInventory().getItem(scanSlot);
            FoodProperties foodProperties = stack.get(DataComponents.FOOD);
            if (!this.canEat(stack)) {
                continue;
            }
            double candidateScore = (this.sortSetting.getValue()).score(stack, foodProperties);
            boolean betterScore = candidateScore > bestScore;
            if (!betterScore && candidateScore == bestScore) {
                Inventory tieBreakInventory = ClientAccess.evernightMinecraft.player.getInventory();
                betterScore = scanSlot == tieBreakInventory.getSelectedSlot();
            }
            if (betterScore) {
                bestScore = candidateScore;
                selectedFoodSlot = scanSlot;
            }
            if (stack.getItem() == Items.CHORUS_FRUIT) {
                if (this.shouldEscape()) {
                    selectedFoodSlot = scanSlot;
                    break;
                }
            }
        }
        return selectedFoodSlot;
    }

    private boolean canEat(ItemStack stack) {
        FoodProperties foodProperties = stack.get(DataComponents.FOOD);
        if (foodProperties == null) {
            return false;
        }
        if (!foodProperties.canAlwaysEat()) {
            if (ClientAccess.evernightMinecraft.player.getFoodData().getFoodLevel() >= (20)) {
                return false;
            }
        }
        Item chorusFruit = Items.CHORUS_FRUIT;
        if (stack.is(chorusFruit)) {
            if (!this.shouldEscape()) {
                return false;
            }
        }
        ListFilterMode selectionMode = this.modeSetting.getValue();
        Item item = stack.getItem();
        if (!ListFilterMode.matches(selectionMode, item, this.itemsSetting.getValue())) {
            return false;
        }
        SuspiciousStewEffects stewEffects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stewEffects != null) {
            for (Entry stewEffect : stewEffects.effects()) {
                MobEffect mobEffect = stewEffect.effect().value();
                if (!mobEffect.isBeneficial()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean shouldEscape() {
        Boolean autoEscapeEnabled = this.autoEscapeSetting.getValue();
        if (!autoEscapeEnabled) {
            return false;
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        for (Player candidatePlayer : level.players()) {
            LocalPlayer localPlayer = ClientAccess.evernightMinecraft.player;
            float distanceToLocalPlayer = candidatePlayer.distanceTo(localPlayer);
            float escapeRadius = 12.0F;
            if (distanceToLocalPlayer <= escapeRadius) {
                SocialRegistry playerExclusionFilter = ClientContext.socialRegistry;
                if (!playerExclusionFilter.isFriendPlayer(candidatePlayer)
                        && candidatePlayer != ClientAccess.evernightMinecraft.player) {
                    return this.canEscapeNow();
                }
            }
        }
        return false;
    }

    private boolean canEscapeNow() {
        AutoEscapeMode mode = this.escapeModeSetting.getValue();
        if (mode == AutoEscapeMode.COOLDOWN) {
            return this.escapeCooldownTimer.hasElapsedAndReset(2500L);
        }
        if (mode == AutoEscapeMode.JOIN_ONLY) {
            return ClientAccess.evernightMinecraft.player.tickCount <= 50;
        }
        return true;
    }

    private boolean isUntilFullEnabled() {
        return this.untilFullSetting.getValue() && this.untilFullSetting.isVisible();
    }

    private boolean shouldStopEating() {
        if (this.continueUntilFull && this.isUntilFullEnabled()) {
            return false;
        }
        if ((this.hungerSetting.getValue()) != -1) {
            int foodLevel = ClientAccess.evernightMinecraft.player.getFoodData().getFoodLevel();
            Integer hungerThreshold = this.hungerSetting.getValue();
            if (foodLevel <= hungerThreshold) {
                return false;
            }
        }
        if ((this.healthSetting.getValue()) == 0) {
            return true;
        }
        float healthAndAbsorption = EntityUtils.getPlayerHealthWithAbsorption();
        Integer healthThreshold = this.healthSetting.getValue();
        return healthAndAbsorption > healthThreshold.intValue();
    }

    private enum SortChoice implements NamedEntry {
        HUNGER("Hunger") {
            @Override
            public double score(ItemStack unusedStack, FoodProperties foodProperties) {
                return foodProperties.nutrition();
            }
        },
        SATURATION("Saturation") {
            @Override
            public double score(ItemStack unusedStack, FoodProperties foodProperties) {
                return foodProperties.saturation();
            }
        },
        HEALTH("Health") {
            @Override
            public double score(ItemStack stack, FoodProperties unusedFoodProperties) {
                if ((Object) stack.get(DataComponents.CONSUMABLE) instanceof Consumable consumable) {
                    for (ConsumeEffect consumeEffect : consumable.onConsumeEffects()) {
                        if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect statusEffects) {
                            int regenerationAmplifier = getRegenerationAmplifier(statusEffects);
                            if (regenerationAmplifier > 0) {
                                return regenerationAmplifier;
                            }
                        }
                    }
                }
                return 0.0;
            }

            static int getRegenerationAmplifier(ApplyStatusEffectsConsumeEffect statusEffects) {
                for (MobEffectInstance effect : statusEffects.effects()) {
                    if (effect.getEffect() == MobEffects.REGENERATION) {
                        return effect.getAmplifier();
                    }
                }
                return 0;
            }
        };
        private final String displayName;

        SortChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }

        public abstract double score(ItemStack stack, FoodProperties foodProperties);
    }
}