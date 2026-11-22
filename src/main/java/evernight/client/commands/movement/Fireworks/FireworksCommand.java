package evernight.client.commands.movement.Fireworks;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.player.InputOverrideManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.modules.movement.Fireworks.FireworksModule;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import evernight.client.commands.Command;

public final class FireworksCommand extends Command {
    private static final FireworksModule fireworksModule = ClientContext.getModule(FireworksModule.class);
    private int launchTicksRemaining;

    public FireworksCommand() {
        super("fireworks");
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    private void useRocket() {
        int selectedSlot;
        int rocketSlot;
        boolean useInventorySwap;
        rocketSlotSelection:
        {
            selectedSlot = InventoryUtils.getSelectedSlot();
            Item rocketItem = Items.FIREWORK_ROCKET;
            rocketSlot = InventoryUtils.findHotbarSlot(rocketItem);
            Item inventoryRocketItem = Items.FIREWORK_ROCKET;
            int inventoryRocketSlot = InventoryUtils.findMenuSlot(inventoryRocketItem);
            useInventorySwap = false;
            if (rocketSlot != -1) {
                LocalPlayer usingItemPlayer = ClientAccess.evernightMinecraft.player;
                if (!usingItemPlayer.isUsingItem()) {
                    break rocketSlotSelection;
                }
                LocalPlayer usedHandPlayer = ClientAccess.evernightMinecraft.player;
                if (usedHandPlayer.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                    break rocketSlotSelection;
                }
            }
            rocketSlot = inventoryRocketSlot;
            useInventorySwap = true;
        }
        this.withTemporaryRocketSlot(selectedSlot, rocketSlot, useInventorySwap, () -> {
            ElapsedTimer rocketUseTimer = fireworksModule.rocketUseTimer;
            rocketUseTimer.reset();
            MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            InteractionHand useHand = InteractionHand.MAIN_HAND;
            gameMode.useItem(player, useHand);
            SwingInteractionManager swingManager = ClientContext.swingManager;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            SwingAction swingAction = SwingAction.INTERACT;
            swingManager.swing(swingHand, swingAction);
        });
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        builder.then(Command.literal("launch").executes(context -> {
            this.launchTicksRemaining = 3;
            return 1;
        }));
    }

    private void withTemporaryRocketSlot(int selectedSlot, int rocketSlot, boolean useInventorySwap, Runnable useRocket) {
        if (rocketSlot != -1) {
            if (useInventorySwap) {
                int menuSlot = InventoryUtils.toMenuSlot(rocketSlot);
                InventoryUtils.swapIntoSelectedSlot(menuSlot);
                useRocket.run();
                menuSlot = InventoryUtils.toMenuSlot(rocketSlot);
                InventoryUtils.swapIntoSelectedSlot(menuSlot);
            } else {
                InventoryUtils.selectHotbarSlot(rocketSlot);
                useRocket.run();
                InventoryUtils.selectHotbarSlot(selectedSlot);
            }
        }
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerPreTickEvent event) {
        if (this.launchTicksRemaining > 0) {
            this.launchTicksRemaining--;
            InputOverrideManager inputOverrides = ClientContext.inputOverrides;
            inputOverrides.requestJump(true);
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            if (!player.onGround()) {
                this.useRocket();
                this.launchTicksRemaining = 0;
            }
        }
    }
}