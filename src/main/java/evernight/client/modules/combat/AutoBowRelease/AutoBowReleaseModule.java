package evernight.client.modules.combat.AutoBowRelease;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

import java.util.function.Predicate;
import evernight.client.modules.combat.Arrows.ArrowsModule;

public class AutoBowReleaseModule extends Module {
    private final Setting<Integer> delaySetting;
    private final Setting<Boolean> crossbowSetting;
    private boolean releasedDuringUse;

    public AutoBowReleaseModule() {
        super("AutoBowRelease", ModuleCategory.COMBAT, "bowspam");
        this.delaySetting = this.add(new NumberSetting<>("Delay", 5, 2, 20));
        this.crossbowSetting = this.add(new BooleanSetting("Crossbow", false));
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerPreTickEvent event) {
        if (this.isWorldUnavailable()) {
            return;
        }
        ArrowsModule arrows = ClientContext.getModule(ArrowsModule.class);
        if (arrows != null && arrows.isAutoShooting()) {
            return;
        }
        if (!this.hasArrowAccess() || ClientAccess.evernightMinecraft.screen != null) {
            return;
        }
        if (this.crossbowSetting.getValue() && this.fireFirstChargedCrossbow()) {
            return;
        }
        this.releaseBowIfReady();
    }

    private void releaseBowIfReady() {
        if (!ClientAccess.evernightMinecraft.player.isUsingItem()) {
            KeyMapping useKey = ClientAccess.evernightMinecraft.options.keyUse;
            if (!useKey.isDown()) {
                this.releasedDuringUse = false;
            }
        }
        if (!this.hasArrowAccess()) {
            return;
        }
        if (!this.isBowReadyToRelease()) {
            return;
        }
        ClientAccess.evernightMinecraft.gameMode.releaseUsingItem(ClientAccess.evernightMinecraft.player);
        KeyMapping useKey = ClientAccess.evernightMinecraft.options.keyUse;
        if (useKey.isDown()) {
            InteractionHand usedHand = ClientAccess.evernightMinecraft.player.getUsedItemHand();
            PacketSender.sendUseItem(usedHand);
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            PacketSender.sendSwing(swingHand);
        }
        this.releasedDuringUse = true;
    }

    private boolean fireFirstChargedCrossbow() {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = ClientAccess.evernightMinecraft.player.getItemInHand(hand);
            Item crossbowItem = Items.CROSSBOW;
            if (stack.is(crossbowItem) && CrossbowItem.isCharged(stack)) {
                PacketSender.sendUseItem(hand);
                InteractionHand swingHand = InteractionHand.MAIN_HAND;
                PacketSender.sendSwing(swingHand);
                return true;
            }
        }
        return false;
    }

    private boolean hasArrowAccess() {
        if (ClientAccess.evernightMinecraft.player.isCreative()) {
            return true;
        }
        Predicate<ItemStack> arrowPredicate = candidate -> {
            return candidate.getItem() instanceof ArrowItem;
        };
        return InventoryUtils.countItems(arrowPredicate) > 0;
    }

    private boolean isBowReadyToRelease() {
        int releaseTicks = this.getReleaseTicks();
        if (!ClientAccess.evernightMinecraft.player.isUsingItem()) {
            return false;
        }
        int useTicks = ClientAccess.evernightMinecraft.player.getTicksUsingItem();
        if (useTicks < releaseTicks) {
            return false;
        }
        ItemStack useStack = ClientAccess.evernightMinecraft.player.getUseItem();
        Item bowItem = Items.BOW;
        return useStack.is(bowItem);
    }

    public int getReleaseTicks() {
        return this.delaySetting.getValue() + (this.releasedDuringUse ? 0 : 1);
    }

    public int getInitialReleaseTicks() {
        return this.delaySetting.getValue() + 1;
    }
}