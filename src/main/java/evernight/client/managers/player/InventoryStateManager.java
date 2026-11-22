package evernight.client.managers.player;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.exploit.XCarry.XCarryModule;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.InventoryActionEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.SelectedSlotChangeEvent;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.managers.world.ServerStateManager;

public final class InventoryStateManager implements ClientAccess {
    private static final long CLICK_WINDOW_MILLIS = 4000L;
    private static final AntiCheatModule antiCheat;
    private static final XCarryModule xCarry;

    static {
        antiCheat = ClientContext.getModule(AntiCheatModule.class);
        xCarry = ClientContext.getModule(XCarryModule.class);
    }

    private final Map<Integer, DeferredSlotUpdate> deferredSlotUpdates = Collections.synchronizedMap(new HashMap<>());
    private final Queue<Long> clickTimestamps = new ArrayDeque<>();
    private final ElapsedTimer lastClickTimer = new ElapsedTimer();
    private final ElapsedTimer enderChestUseTimer = new ElapsedTimer();
    private volatile boolean containerOpen;
    private volatile boolean clickBatching;
    private volatile boolean clickBatchCompleted;
    private boolean localEntityEventPending;
    private int serverSelectedSlot = -1;
    private boolean slotResyncArmed;

    public InventoryStateManager() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    public static boolean wouldReachClickLimit(InventoryStateManager manager, int additionalClicks) {
        java.util.Objects.requireNonNull(manager);
        return antiCheat.is2b2tEnabled() && manager.clickTimestamps.size() + additionalClicks >= 71;
    }

    public static boolean isClickRateLimited(InventoryStateManager manager) {
        return wouldReachClickLimit(manager, 0);
    }

    public void setClickBatching(boolean batching) {
        if (this.clickBatching != batching) {
            this.clickBatching = batching;
            if (!(ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen)) {
                if (RotationManager.isMovementSyncEnabled()) {
                    XCarryModule xCarryModule = xCarry;
                    if (!xCarryModule.isToggled() && !this.clickBatching && this.clickBatchCompleted) {
                        this.close();
                    }
                }
            }
            this.clickBatchCompleted = false;
        }
    }

    public void close() {
        Packet packet = new ServerboundContainerClosePacket(ClientAccess.evernightMinecraft.player.containerMenu.containerId);
        PacketSender.send(packet);
        this.containerOpen = false;
    }

    private void resendHeldSlot(int slot) {
        if (RotationManager.isMovementSyncEnabled() && this.slotResyncArmed) {
            PacketSender.sendSelectedSlot((slot + 1) % 9);
            PacketSender.sendSelectedSlot(slot);
            this.slotResyncArmed = true;
        }
    }

    @EventHandler
    private void onConnectionDoSendPacketEvent(PacketEvent.PostSend event) {
        if (event.getPacket() instanceof ServerboundContainerClickPacket && (!this.clickBatching
                || !this.clickBatchCompleted)) {
            EventBus capturedEventBus = ClientAccess.eventBus;
            InventoryActionEvent postEvent = new InventoryActionEvent(EventStage.Post);
            capturedEventBus.post(postEvent);
            this.clickBatchCompleted = true;
        }
    }

    @EventHandler
    private void onClientPacketListenerHandleSetHeldSlotEvent(
            SelectedSlotChangeEvent.ClientPacketListenerHandleSetHeldSlotEvent event) {
        this.serverSelectedSlot = event.getSlot();
        int selectedSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        if (!minecraft.isLocalServer()) {
            Setting<Boolean> noServerSlotSetting = antiCheat.noServerSlotSetting;
            if (noServerSlotSetting.getValue()) {
                int slotWord = event.getSlot();
                this.resendHeldSlot(slotWord);
                if (ClientAccess.evernightMinecraft.player.tickCount < (5)) {
                    return;
                }
                event.cancel();
                if (RotationManager.isMovementSyncEnabled()) {
                    TaskScheduler scheduler = ClientContext.taskScheduler;
                    Runnable restoreHeldSlot = () -> {
                        PacketSender.sendSelectedSlot(selectedSlot);
                        if (ClientAccess.evernightMinecraft.player.isUsingItem()) {
                            MultiPlayerGameMode releaseGameMode = ClientAccess.evernightMinecraft.gameMode;
                            LocalPlayer releasePlayer = ClientAccess.evernightMinecraft.player;
                            releaseGameMode.releaseUsingItem(releasePlayer);
                        }
                    };
                    scheduler.scheduleRotationTask(restoreHeldSlot, 0);
                } else {
                    PacketSender.sendSelectedSlot(selectedSlot);
                }
            }
        }
    }

    private boolean matchesOtherHeldStack(ItemStack stack, int menuSlot) {
        ItemStack offhandStack = ClientAccess.evernightMinecraft.player.getOffhandItem();
        if (ItemStack.matches(offhandStack, stack)) {
            return true;
        }
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            if (menuSlot - (36) != hotbarSlot) {
                ItemStack hotbarStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(hotbarSlot);
                if (ItemStack.matches(hotbarStack, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = -100)
    private void onMultiPlayerGameModeHandleInventoryMouseClickEvent(
            MultiPlayerGameModeHandleInventoryMouseClickEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        Runnable recordClick = this::recordClick;
        minecraft.execute(recordClick);
        this.containerOpen = true;
        this.lastClickTimer.reset();
        if (!this.clickBatching || !this.clickBatchCompleted) {
            EventBus capturedEventBus = ClientAccess.eventBus;
            InventoryActionEvent preEvent = new InventoryActionEvent(EventStage.Pre);
            capturedEventBus.post(preEvent);
        }
    }

    private void recordClick() {
        Queue<Long> timestamps = this.clickTimestamps;
        long now = System.currentTimeMillis();
        timestamps.add(now);
    }

    @EventHandler(priority = -100)
    private void onInventoryClickAfter(MultiPlayerGameModeHandleInventoryMouseClickEvent.After event) {
        if (!this.clickBatching || !this.clickBatchCompleted) {
            EventBus capturedEventBus = ClientAccess.eventBus;
            InventoryActionEvent postEvent = new InventoryActionEvent(EventStage.Post);
            capturedEventBus.post(postEvent);
            this.clickBatchCompleted = true;
        }
    }

    @EventHandler
    private void onMultiPlayerGameModeUseItemOnEvent(MultiPlayerGameModeUseItemOnEvent event) {
        LocalPlayer sneakPlayer = ClientAccess.evernightMinecraft.player;
        if (!sneakPlayer.isShiftKeyDown()) {
            BlockPos capturedPosition = event.getHitResult().getBlockPos();
            if (WorldUtils.getBlock(capturedPosition) == Blocks.ENDER_CHEST) {
                this.enderChestUseTimer.reset();
            }
        }
    }

    @EventHandler(priority = 200)
    private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
        this.clickTimestamps.removeIf(timestamp -> {
            long ageMillis = System.currentTimeMillis();
            ageMillis -= timestamp;
            return ageMillis >= CLICK_WINDOW_MILLIS;
        });
    }

    @EventHandler(priority = -100)
    private void onConnectionSendPacketEvent(PacketEvent.Send event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getPacket() instanceof ServerboundContainerClickPacket) {
            this.containerOpen = true;
            this.lastClickTimer.reset();
            if (!this.clickBatching || !this.clickBatchCompleted) {
                EventBus capturedPreEventBus = ClientAccess.eventBus;
                InventoryActionEvent preEvent = new InventoryActionEvent(EventStage.Pre);
                capturedPreEventBus.post(preEvent);
            }
        }
        if (event.getPacket() instanceof ServerboundContainerClosePacket
                || event.getPacket() instanceof ClientboundDisconnectPacket) {
            this.containerOpen = false;
        }
        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket carriedItemPacket) {
            if (carriedItemPacket.getSlot() != this.serverSelectedSlot) {
                EventBus capturedSlotEventBus = ClientAccess.eventBus;
                SelectedSlotChangeEvent slotChangeEvent = new SelectedSlotChangeEvent(carriedItemPacket.getSlot());
                capturedSlotEventBus.post(slotChangeEvent);
                this.serverSelectedSlot = carriedItemPacket.getSlot();
            }
        }
        if (event.getPacket() instanceof ClientboundDisconnectPacket) {
            this.serverSelectedSlot = -1;
        }
    }

    @EventHandler(priority = -200)
    private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
        if (event.getStage() == EventStage.Post) {
            return;
        }
        synchronized (this.deferredSlotUpdates) {
            ArrayList<Integer> readySlots = new ArrayList<>();
            this.deferredSlotUpdates.forEach((slot, update) -> {
                        if (System.currentTimeMillis() >= update.resumeAtMillis()) {
                            readySlots.add(slot);
                        }
                    }
            );
            Iterator<Integer> slotsIterator = readySlots.iterator();
            while (slotsIterator.hasNext()) {
                Integer readySlot = slotsIterator.next();
                InventoryStateManager.DeferredSlotUpdate removedUpdate = this.deferredSlotUpdates.remove(readySlot);
                ClientPacketListener capturedConnection = ClientAccess.evernightMinecraft.player.connection;
                ClientboundContainerSetSlotPacket packet = removedUpdate.packet();
                capturedConnection.handleContainerSetSlot(packet);
            }
        }
        this.localEntityEventPending = false;
        if (xCarry.isToggled() || ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen) {
            return;
        }
        ElapsedTimer capturedTimer = this.lastClickTimer;
        if (!capturedTimer.hasElapsed(50L) || !this.containerOpen) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        ServerStateManager capturedTickTracker = ClientContext.serverStateManager;
        if ((nowMillis - capturedTickTracker.getLastPacketTime()) > 2500L) {
            return;
        }
        if (RotationManager.is2b2tMode()) {
            capturedTimer = this.enderChestUseTimer;
            if (!capturedTimer.hasElapsed(200L)) {
                return;
            }
        }
        this.close();
    }

    @EventHandler(priority = 100)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundLoginPacket) {
            this.slotResyncArmed = true;
        }
        if (event.getPacket() instanceof ClientboundContainerClosePacket closePacket) {
            this.containerOpen = false;
        }
        if (event.getPacket() instanceof ClientboundContainerSetContentPacket) {
            this.deferredSlotUpdates.clear();
        }
        if (event.getPacket() instanceof ClientboundContainerSetSlotPacket slotPacket) {
            if (RotationManager.isMovementSyncEnabled()) {
                int slotWord = slotPacket.getSlot();
                Inventory inventory = ClientAccess.evernightMinecraft.player.getInventory();
                if (slotWord == (36) + inventory.getSelectedSlot()) {
                    return;
                }
                slotWord = slotPacket.getSlot();
                if (slotWord < (36)) {
                    return;
                }
                slotWord = slotPacket.getSlot();
                if (slotWord > (44)) {
                    return;
                }
                ItemStack capturedItem = slotPacket.getItem();
                int itemSlot = slotPacket.getSlot();
                if (!this.matchesOtherHeldStack(capturedItem, itemSlot)) {
                    Map<Integer, DeferredSlotUpdate> removeMap = this.deferredSlotUpdates;
                    int removeSlot = slotPacket.getSlot();
                    removeMap.remove(removeSlot);
                    return;
                }
                Map<Integer, DeferredSlotUpdate> deferredMap = this.deferredSlotUpdates;
                int deferredSlot = slotPacket.getSlot();
                deferredMap.put(deferredSlot, new InventoryStateManager.DeferredSlotUpdate(slotPacket, System.currentTimeMillis() + 500L));
                event.cancel();
            }
        }
        if (event.getPacket() instanceof ClientboundEntityEventPacket entityPacket) {
            if (entityPacket.getEventId() == 35) {
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                if (entityPacket.getEntity(level) == ClientAccess.evernightMinecraft.player) {
                    this.localEntityEventPending = true;
                }
            }
        }
    }

    public boolean hasPendingLocalEntityEvent() {
        return this.localEntityEventPending;
    }

    private record DeferredSlotUpdate(ClientboundContainerSetSlotPacket packet, long resumeAtMillis) {
    }
}