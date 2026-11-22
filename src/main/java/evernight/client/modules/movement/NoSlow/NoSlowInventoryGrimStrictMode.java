package evernight.client.modules.movement.NoSlow;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.InventoryActionEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.integrations.ProtocolCompatibility;
import evernight.client.managers.player.RotationManager;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.NoSlowInventoryMode;
import evernight.client.utils.network.PacketSender;
import evernight.mixin.minecraft.entity.LocalPlayerAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Queue;

public class NoSlowInventoryGrimStrictMode extends NoSlowInventoryMode {
    private final Queue<Runnable> pendingActions = new ArrayDeque<>();
    private boolean flushPending;
    private boolean replayingAction;
    private boolean inputActive;
    private boolean inventoryInputSuppressed;

    public NoSlowInventoryGrimStrictMode(NoSlowModule noSlow) {
        super(noSlow);
    }

    private void flushQueuedActions() {
        if (this.pendingActions.isEmpty()) return;
        long attempts = 0L;
        while (!this.pendingActions.isEmpty()) {
            if (++attempts > 50L) break;
            Runnable action = this.pendingActions.poll();
            if (action != null) this.runReplayedAction(action);
        }
        this.pendingActions.clear();
    }

    private boolean isDelayablePacket(Packet packet) {
        return packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundSwingPacket
                || packet instanceof PosRot && RotationManager.isSilentRotationMode();
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (this.replayingAction) return;
        if (this.usesLegacyClickProtocol()) return;
        Packet packet = event.getPacket();
        if (this.isDelayablePacket(packet) && this.inputActive && !this.pendingActions.isEmpty()) {
            this.queueOrRunAction(() -> {
                PacketSender.send(event.getPacket());
            });
            event.cancel();
        }
        if (event.getPacket() instanceof ServerboundContainerClosePacket closePacket && this.inputActive) {
            this.queueOrRunAction(() -> PacketSender.send(closePacket));
            event.cancel();
        }
        if (event.getPacket() instanceof ServerboundPlayerCommandPacket command) {
            if (command.getAction() == Action.START_FALL_FLYING) {
                this.inputActive = false;
                this.flushQueuedActions();
            }
        }
    }

    @Override
    public void onInventoryClickIntercept(MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event) {
        if (this.usesLegacyClickProtocol()) return;
        if (this.replayingAction || !this.inputActive) return;
        int containerId = event.getContainerId();
        int slotId = event.getSlotId();
        int button = event.getButton();
        ClickType clickType = event.getClickType();
        this.queueOrRunAction(() -> {
            ClientAccess.evernightMinecraft.gameMode.handleInventoryMouseClick(containerId, slotId, button, clickType, ClientAccess.evernightMinecraft.player);
        });
        event.cancel();
    }

    @Override
    public void beforeMenuClick(MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event) {
        if (this.usesLegacyClickProtocol()) return;
        if (this.replayingAction) {
            event.cancel();
        }
    }

    @Override
    public void onTickStart(LocalPlayerPreTickEvent event) {
        if (this.flushPending) {
            this.flushQueuedActions();
            this.flushPending = false;
        }
        this.inputActive = this.hasPlayerInput();
        boolean keepPendingFlush = false;
        if (this.inputActive && ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen) {
            ItemStack cursorStack = InventoryUtils.getCarriedStack();
            keepPendingFlush = !cursorStack.isEmpty();
        }
        if (!keepPendingFlush) this.flushPending = !this.pendingActions.isEmpty();
        if (this.flushPending) {
            ClientContext.inputOverrides.requestReset();
            ClientAccess.evernightMinecraft.player.setSprinting(false);
        }
    }

    @Override
    public void onInventoryAction(InventoryActionEvent event) {
        if (this.usesLegacyClickProtocol() && !this.inventoryInputSuppressed) {
            Input emptyInput = new Input(false, false, false, false, false, false, false);
            PacketSender.send(new ServerboundPlayerInputPacket(emptyInput));
            PacketSender.sendPlayerCommand(Action.STOP_SPRINTING);
            ((LocalPlayerAccessor) ClientAccess.evernightMinecraft.player).evernight$setLastPlayerInput(emptyInput);
            ((LocalPlayerAccessor) ClientAccess.evernightMinecraft.player).evernight$setLastSprinting(false);
            ClientContext.inputOverrides.blockSprint();
            this.inventoryInputSuppressed = true;
        }
    }

    private boolean canQueueInCurrentScreen() {
        if (ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen screen) {
            if (!this.noSlow.containersSetting.getValue()) {
                return screen.getMenu() == ClientAccess.evernightMinecraft.player.inventoryMenu;
            }
        }
        return true;
    }

    private boolean hasPlayerInput() {
        Input input = ClientAccess.evernightMinecraft.player.input.keyPresses;
        if (input.forward()) return true;
        if (input.backward()) return true;
        if (input.left()) return true;
        if (input.right()) return true;
        if (input.jump()) return true;
        if (input.shift()) return true;
        return input.sprint();
    }

    private synchronized void runReplayedAction(Runnable action) {
        this.replayingAction = true;
        action.run();
        this.replayingAction = false;
    }

    @Override
    public void onPlayerAiStep() {
        if (this.flushPending || this.inventoryInputSuppressed) {
            ClientAccess.evernightMinecraft.player.setSprinting(false);
        }
    }

    public boolean isReplayingAction() {
        return this.replayingAction;
    }

    @Override
    public void onTickBeforePassengerCheck() {
        this.inputActive = this.hasPlayerInput();
        this.inventoryInputSuppressed = false;
    }

    private boolean usesLegacyClickProtocol() {
        return ProtocolCompatibility.getTargetVersion().olderEq_1_21_5();
    }

    public synchronized void queueOrRunAction(Runnable action) {
        if (this.replayingAction) return;
        if (this.noSlow.guiMoveSetting.getValue()) {
            if (this.canQueueInCurrentScreen()) {
                this.pendingActions.add(action);
                return;
            }
        }
        this.runReplayedAction(action);
    }
}