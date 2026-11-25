package evernight.client.modules.player.AutoTransport;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.RotationManager;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.chat.RandomTagReplacer;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.ContainerInteractionUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.ItemFrameContainerLocator;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * State machine behind {@link AutoTransportModule}.
 *
 * <p>Each tick advances exactly one phase. Container phases open the marked chest or the nearest
 * pre-placed ender chest, quick-move shulker boxes and close the screen again; the pearl phases
 * refill from the marked pearl chest and throw at the recorded point. The machine is purely client
 * side: it only sends the same clicks and use-item packets a player would send.
 *
 * <p>A position jump larger than the configured teleport distance is treated as an arrival on every
 * phase, because the partner may trigger the pearl before the local player has finished loading.
 */
public final class AutoTransportController implements ClientAccess {
    private static final int PHASE_TIMEOUT_TICKS = 600;
    private static final int OPEN_ATTEMPT_MILLIS = 500;
    private static final int MAX_OPEN_ATTEMPTS = 20;
    private static final int PAUSE_RETRY_MILLIS = 1000;
    private static final int CLOSE_GRACE_MILLIS = 1000;
    private static final int THROW_AIM_MILLIS = 100;
    private static final int FAILURE_REPEAT_MILLIS = 10_000;
    private static final int MIN_CONTAINER_MENU_SLOTS = 27 + 36;
    private static final int REQUIRED_PEARLS = 16;

    private enum Side {
        SOURCE,
        DESTINATION
    }

    private final AutoTransportModule module;
    private final ElapsedTimer phaseTimer;
    private final ElapsedTimer clickTimer;
    private final ElapsedTimer retryTimer;
    private final ElapsedTimer openTimer;
    private final ElapsedTimer throwAimTimer;
    private final ElapsedTimer closeGraceTimer;
    private final ElapsedTimer failureTimer;
    private final ElapsedTimer scanTimer;

    private AutoTransportPhase phase;
    private AutoTransportPhase pausedPhase;
    private Side side;
    private BlockPos target;
    private Vec3 lastTickPosition;
    private ClientLevel lastLevel;
    private int phaseTicks;
    private int openAttempts;
    private boolean moduleScreenOpen;
    private boolean closingScreen;
    private boolean openRequested;
    private boolean pathActive;
    private boolean throwAimed;
    private boolean noCargoChestNotified;
    private boolean inventoryFullNotified;
    private boolean containerFullNotified;
    private boolean pearlCooldownNotified;
    private String lastFailureKey;

    public AutoTransportController(AutoTransportModule module) {
        this.module = module;
        this.phaseTimer = new ElapsedTimer();
        this.clickTimer = new ElapsedTimer();
        this.retryTimer = new ElapsedTimer();
        this.openTimer = new ElapsedTimer();
        this.throwAimTimer = new ElapsedTimer();
        this.closeGraceTimer = new ElapsedTimer();
        this.failureTimer = new ElapsedTimer();
        this.scanTimer = new ElapsedTimer();
        this.phase = AutoTransportPhase.WAIT_RECALL;
        this.pausedPhase = AutoTransportPhase.WAIT_RECALL;
        this.side = Side.SOURCE;
    }

    public String getPhaseName() {
        return this.phase.name();
    }

    public void start() {
        this.resetState();
        this.lastLevel = evernightMinecraft.level;
        this.side = this.resolveInitialSide();
        this.enterPhase(AutoTransportPhase.WAIT_RECALL);
    }

    public void shutdown() {
        this.cancelPathing();
        this.closeScreenGracefully();
        this.closingScreen = false;
        this.resetState();
    }

    public void tick() {
        if (this.isWorldUnavailable()) {
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (player == null || player.isDeadOrDying() || player.isSpectator()) {
            return;
        }
        ClientLevel level = evernightMinecraft.level;
        if (level == null) {
            return;
        }
        if (level != this.lastLevel) {
            this.lastLevel = level;
            this.start();
            return;
        }
        Vec3 position = player.position();
        if (this.lastTickPosition != null
                && position.distanceTo(this.lastTickPosition) > this.module.getTeleportDistance()) {
            this.lastTickPosition = position;
            this.onArrived();
            return;
        }
        this.lastTickPosition = position;
        this.phaseTicks++;
        switch (this.phase) {
            case WAIT_RECALL -> this.waitRecall();
            case DETECT_PEARL -> this.detectPearl();
            case REFILL_PEARL -> this.refillPearls();
            case THROW_PEARL -> this.throwPearl();
            case THROW_SETTLE -> this.throwSettle();
            case SOURCE_LOAD -> this.moveChestToInventory(
                    this::findCargoChest, false, "NoCargoChest", AutoTransportPhase.SOURCE_STORE);
            case SOURCE_STORE -> this.moveInventoryToChest(
                    this::findEnderChest, true, "NoEnderChest", AutoTransportPhase.SOURCE_REFILL);
            case SOURCE_REFILL -> this.moveChestToInventory(
                    this::findCargoChest, false, "NoCargoChest", AutoTransportPhase.REQUEST);
            case DEST_UNLOAD -> this.moveInventoryToChest(
                    this::findCargoChest, false, "NoCargoChest", AutoTransportPhase.DEST_TAKE);
            case DEST_TAKE -> this.moveChestToInventory(
                    this::findEnderChest, true, "NoEnderChest", AutoTransportPhase.DEST_UNLOAD2);
            case DEST_UNLOAD2 -> this.moveInventoryToChest(
                    this::findCargoChest, false, "NoCargoChest", AutoTransportPhase.REQUEST);
            case REQUEST -> this.sendRequest();
            case TRANSIT -> {
            }
            case PAUSED -> {
                if (this.retryTimer.hasElapsedAndReset(PAUSE_RETRY_MILLIS)) {
                    this.enterPhase(this.pausedPhase);
                }
            }
        }
    }

    private void resetState() {
        this.cancelPathing();
        this.phase = AutoTransportPhase.WAIT_RECALL;
        this.pausedPhase = AutoTransportPhase.WAIT_RECALL;
        this.target = null;
        this.lastTickPosition = null;
        this.phaseTicks = 0;
        this.openAttempts = 0;
        this.moduleScreenOpen = false;
        this.closingScreen = false;
        this.openRequested = false;
        this.throwAimed = false;
        this.noCargoChestNotified = false;
        this.inventoryFullNotified = false;
        this.containerFullNotified = false;
        this.pearlCooldownNotified = false;
        this.lastFailureKey = null;
    }

    private void enterPhase(AutoTransportPhase nextPhase) {
        this.phase = nextPhase;
        this.phaseTicks = 0;
        this.target = null;
        this.openAttempts = 0;
        this.openRequested = false;
        this.throwAimed = false;
        this.noCargoChestNotified = false;
        this.inventoryFullNotified = false;
        this.containerFullNotified = false;
        this.pearlCooldownNotified = false;
        this.phaseTimer.reset();
        this.retryTimer.reset();
        this.openTimer.reset();
        this.throwAimTimer.reset();
        this.scanTimer.reset();
        this.clickTimer.clear();
        if (this.module.isDebug() && nextPhase != AutoTransportPhase.PAUSED) {
            this.module.notifyAlways("DebugPhase", nextPhase.name());
        }
    }

    private void pause(String key, Object... args) {
        if (this.phase != AutoTransportPhase.PAUSED) {
            this.pausedPhase = this.phase;
        }
        boolean repeated = key.equals(this.lastFailureKey) && !this.failureTimer.hasElapsed(FAILURE_REPEAT_MILLIS);
        this.lastFailureKey = key;
        if (!repeated) {
            this.failureTimer.reset();
            this.module.notifyError(key, args);
        }
        this.cancelPathing();
        this.closeScreenGracefully();
        this.enterPhase(AutoTransportPhase.PAUSED);
    }

    private void checkPhaseTimeout() {
        if (this.phaseTicks > PHASE_TIMEOUT_TICKS) {
            this.pause("NoProgress");
        }
    }

    private void onArrived() {
        this.cancelPathing();
        this.closeScreenGracefully();
        if (this.module.getSideChoice() == AutoTransportSideChoice.AUTO) {
            this.side = this.resolveArrivalSide();
        }
        this.module.notifyInfo("Arrived");
        this.enterPhase(AutoTransportPhase.DETECT_PEARL);
    }

    private Side resolveInitialSide() {
        AutoTransportSideChoice choice = this.module.getSideChoice();
        if (choice == AutoTransportSideChoice.SOURCE) {
            return Side.SOURCE;
        }
        if (choice == AutoTransportSideChoice.DESTINATION) {
            return Side.DESTINATION;
        }
        BlockPos sourcePoint = this.module.getSourceThrowPoint();
        BlockPos destinationPoint = this.module.getDestinationThrowPoint();
        Vec3 position = evernightMinecraft.player.position();
        if (sourcePoint != null && destinationPoint != null) {
            double sourceDistance = position.distanceToSqr(sourcePoint.getCenter());
            double destinationDistance = position.distanceToSqr(destinationPoint.getCenter());
            return sourceDistance <= destinationDistance ? Side.SOURCE : Side.DESTINATION;
        }
        double search = this.module.getScanRange() * 2.0D;
        if (sourcePoint != null && position.distanceToSqr(sourcePoint.getCenter()) <= search * search) {
            return Side.SOURCE;
        }
        if (destinationPoint != null && position.distanceToSqr(destinationPoint.getCenter()) <= search * search) {
            return Side.DESTINATION;
        }
        return Side.SOURCE;
    }

    private Side resolveArrivalSide() {
        if (this.module.getSideChoice() != AutoTransportSideChoice.AUTO) {
            return this.side;
        }
        BlockPos sourcePoint = this.module.getSourceThrowPoint();
        BlockPos destinationPoint = this.module.getDestinationThrowPoint();
        if (sourcePoint != null && destinationPoint != null) {
            Vec3 position = evernightMinecraft.player.position();
            double sourceDistance = position.distanceToSqr(sourcePoint.getCenter());
            double destinationDistance = position.distanceToSqr(destinationPoint.getCenter());
            return sourceDistance <= destinationDistance ? Side.SOURCE : Side.DESTINATION;
        }
        return this.side == Side.SOURCE ? Side.DESTINATION : Side.SOURCE;
    }

    private void waitRecall() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null || !player.onGround()) {
            return;
        }
        if (!this.scanTimer.hasElapsedAndReset(PAUSE_RETRY_MILLIS)) {
            return;
        }
        if (this.findCargoChest() == null) {
            if (!this.noCargoChestNotified) {
                this.noCargoChestNotified = true;
                this.module.notifyInfo("NoCargoChest");
            }
            return;
        }
        this.enterPhase(AutoTransportPhase.DETECT_PEARL);
    }

    private void detectPearl() {
        int pearls = InventoryUtils.countItems(stack -> stack.is(Items.ENDER_PEARL));
        if (pearls <= 0) {
            this.pause("NoPearl");
            return;
        }
        this.enterPhase(pearls == 1 ? AutoTransportPhase.REFILL_PEARL : AutoTransportPhase.THROW_PEARL);
    }

    private void refillPearls() {
        if (this.target == null) {
            this.target = this.findPearlChest();
            if (this.target == null) {
                this.pause("NoPearlChest");
                return;
            }
        }
        if (!this.ensureContainer(this.target, false)) {
            return;
        }
        AbstractContainerMenu menu = evernightMinecraft.player.containerMenu;
        int pearlCount = InventoryUtils.countItems(stack -> stack.is(Items.ENDER_PEARL));
        if (pearlCount >= REQUIRED_PEARLS) {
            this.closeAndAdvance(AutoTransportPhase.THROW_PEARL);
            return;
        }
        if (InventoryUtils.isInventoryFull()) {
            this.notifyInventoryFullOnce();
            this.closeAndAdvance(AutoTransportPhase.THROW_PEARL);
            return;
        }
        int pearlSlot = this.findContainerSlot(menu, stack -> stack.is(Items.ENDER_PEARL));
        if (pearlSlot < 0) {
            this.closeAndAdvance(AutoTransportPhase.THROW_PEARL);
            return;
        }
        this.clickOnce(menu, pearlSlot);
        this.checkPhaseTimeout();
    }

    private void throwPearl() {
        if (this.closingScreen) {
            if (evernightMinecraft.screen == null) {
                this.closingScreen = false;
            }
            return;
        }
        if (evernightMinecraft.screen != null) {
            this.closeScreenGracefully();
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        int pearls = InventoryUtils.countItems(stack -> stack.is(Items.ENDER_PEARL));
        if (pearls <= 0) {
            this.pause("NoPearl");
            return;
        }
        if (PlayerStateUtils.isEnderPearlOnCooldown()) {
            if (!this.pearlCooldownNotified) {
                this.pearlCooldownNotified = true;
                this.module.notifyInfo("PearlCooldown");
            }
            this.checkPhaseTimeout();
            return;
        }
        BlockPos throwPoint = this.getThrowPoint();
        if (throwPoint != null && player.blockPosition().distSqr(throwPoint) > 9.0D) {
            if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
                ClientContext.baritoneBridge.pathTo(throwPoint);
                this.pathActive = true;
            }
            this.checkPhaseTimeout();
            return;
        }
        this.cancelPathing();
        float[] angles = throwPoint != null
                ? RotationManager.calculateRotations(player.getEyePosition(), throwPoint.getCenter())
                : new float[]{player.getYRot(), this.module.getThrowPitch()};
        ClientContext.rotationManager.requestRotation(angles, 100);
        if (!this.throwAimed) {
            this.throwAimed = true;
            this.throwAimTimer.reset();
            return;
        }
        if (!this.throwAimTimer.hasElapsed(THROW_AIM_MILLIS)) {
            return;
        }
        InventoryUtils.SwapSlot pearlSlot = InventoryUtils.findSwapSlot(Items.ENDER_PEARL, true);
        if (!pearlSlot.isFound()) {
            this.pause("NoPearl");
            return;
        }
        int previousSlot = InventoryUtils.getSelectedSlot();
        ClientContext.playerStateTracker.runSneaking(() -> {
            InventoryUtils.swapTo(pearlSlot);
            try {
                evernightMinecraft.gameMode.useItem(player, pearlSlot.hand());
                PacketSender.sendSwing(pearlSlot.hand());
            } finally {
                InventoryUtils.swapBack(previousSlot, pearlSlot);
            }
        });
        this.enterPhase(AutoTransportPhase.THROW_SETTLE);
    }

    private void throwSettle() {
        if (!this.phaseTimer.hasElapsed(this.module.getThrowDelay())) {
            return;
        }
        this.enterPhase(this.side == Side.SOURCE ? AutoTransportPhase.SOURCE_LOAD : AutoTransportPhase.DEST_UNLOAD);
    }

    private void moveChestToInventory(Supplier<BlockPos> locator, boolean enderChest, String missingKey,
                                      AutoTransportPhase nextPhase) {
        if (this.target == null) {
            this.target = locator.get();
            if (this.target == null) {
                this.pause(missingKey);
                return;
            }
        }
        if (!this.ensureContainer(this.target, enderChest)) {
            return;
        }
        AbstractContainerMenu menu = evernightMinecraft.player.containerMenu;
        if (InventoryUtils.isInventoryFull()) {
            this.notifyInventoryFullOnce();
            this.closeAndAdvance(nextPhase);
            return;
        }
        int sourceSlot = this.findContainerSlot(menu, AutoTransportController::isShulkerBoxStack);
        if (sourceSlot < 0) {
            this.closeAndAdvance(nextPhase);
            return;
        }
        this.clickOnce(menu, sourceSlot);
        this.checkPhaseTimeout();
    }

    private void moveInventoryToChest(Supplier<BlockPos> locator, boolean enderChest, String missingKey,
                                      AutoTransportPhase nextPhase) {
        if (this.target == null) {
            this.target = locator.get();
            if (this.target == null) {
                this.pause(missingKey);
                return;
            }
        }
        if (!this.ensureContainer(this.target, enderChest)) {
            return;
        }
        AbstractContainerMenu menu = evernightMinecraft.player.containerMenu;
        int destinationSlot = this.findEmptyContainerSlot(menu);
        if (destinationSlot < 0) {
            if (!this.containerFullNotified) {
                this.containerFullNotified = true;
                this.module.notifyInfo("ContainerFull");
            }
            this.closeAndAdvance(nextPhase);
            return;
        }
        int inventorySlot = this.findInventoryShulkerSlot(menu);
        if (inventorySlot < 0) {
            this.closeAndAdvance(nextPhase);
            return;
        }
        this.clickOnce(menu, inventorySlot);
        this.checkPhaseTimeout();
    }

    private void sendRequest() {
        String template = this.side == Side.SOURCE
                ? this.module.getSourceRequestCommand()
                : this.module.getDestinationRequestCommand();
        if (template == null || template.isBlank()) {
            this.module.notifyInfo("RequestEmpty");
        } else {
            if (evernightMinecraft.getConnection() == null) {
                this.pause("NoConnection");
                return;
            }
            String message = RandomTagReplacer.apply(template.trim());
            ChatUtils.sendChatOrCommand(message);
            this.module.notifyInfo("RequestSent");
        }
        this.enterPhase(AutoTransportPhase.TRANSIT);
    }

    private boolean ensureContainer(BlockPos position, boolean enderChestExpected) {
        if (this.phaseTicks > PHASE_TIMEOUT_TICKS) {
            this.pause("NoProgress");
            return false;
        }
        if (this.closingScreen) {
            if (evernightMinecraft.screen == null) {
                this.closingScreen = false;
            } else if (this.closeGraceTimer.hasElapsed(CLOSE_GRACE_MILLIS)) {
                this.closingScreen = false;
            } else {
                return false;
            }
        }
        Screen screen = evernightMinecraft.screen;
        LocalPlayer player = evernightMinecraft.player;
        if (screen != null) {
            if (this.openRequested && this.isExpectedContainerScreen(screen, enderChestExpected)) {
                this.moduleScreenOpen = true;
                return true;
            }
            this.pause("ScreenBusy");
            return false;
        }
        this.moduleScreenOpen = false;
        if (player.blockPosition().distSqr(position) > 9.0D) {
            if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
                ClientContext.baritoneBridge.pathTo(position);
                this.pathActive = true;
            }
            return false;
        }
        this.cancelPathing();
        if (this.openTimer.hasElapsedAndReset(OPEN_ATTEMPT_MILLIS)) {
            ContainerInteractionUtils.openContainer(position, this.module.isRotate());
            this.openRequested = true;
            this.openAttempts++;
        }
        if (this.openAttempts > MAX_OPEN_ATTEMPTS) {
            this.pause("ChestOpenTimeout");
        }
        return false;
    }

    private boolean isExpectedContainerScreen(Screen screen, boolean enderChestExpected) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        AbstractContainerMenu menu = evernightMinecraft.player.containerMenu;
        if (menu == null || menu.slots.size() < MIN_CONTAINER_MENU_SLOTS) {
            return false;
        }
        return InventoryUtils.isEnderChestScreen(screen) == enderChestExpected;
    }

    private void closeAndAdvance(AutoTransportPhase nextPhase) {
        this.closeScreenGracefully();
        this.enterPhase(nextPhase);
    }

    private void closeScreenGracefully() {
        if (this.moduleScreenOpen && evernightMinecraft.screen != null) {
            ContainerInteractionUtils.closeScreen();
            this.closingScreen = true;
            this.closeGraceTimer.reset();
        }
        this.moduleScreenOpen = false;
        this.openRequested = false;
    }

    private void cancelPathing() {
        if (this.pathActive) {
            ClientContext.baritoneBridge.cancelPathing();
            this.pathActive = false;
        }
    }

    private void clickOnce(AbstractContainerMenu menu, int slot) {
        if (slot < 0 || slot >= menu.slots.size()) {
            return;
        }
        if (!this.clickTimer.hasElapsedAndReset(this.module.getClickDelay())) {
            return;
        }
        InventoryUtils.clickSlot(menu.containerId, slot, 0, ClickType.QUICK_MOVE);
    }

    private void notifyInventoryFullOnce() {
        if (!this.inventoryFullNotified) {
            this.inventoryFullNotified = true;
            this.module.notifyInfo("InventoryFull");
        }
    }

    private BlockPos findCargoChest() {
        return ItemFrameContainerLocator
                .findNearestContainer(AutoTransportController::isShulkerBoxStack, this.module.getScanRange())
                .orElse(null);
    }

    private BlockPos findPearlChest() {
        return ItemFrameContainerLocator
                .findNearestContainer(stack -> stack.is(Items.ENDER_PEARL), this.module.getScanRange())
                .orElse(null);
    }

    private BlockPos findEnderChest() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return null;
        }
        Vec3 position = player.position();
        double range = this.module.getScanRange();
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        List<BlockEntity> blockEntities = WorldUtils.getLoadedBlockEntities();
        for (BlockEntity blockEntity : blockEntities) {
            if (!(blockEntity instanceof EnderChestBlockEntity)) {
                continue;
            }
            BlockPos candidate = blockEntity.getBlockPos();
            double distance = position.distanceTo(candidate.getCenter());
            if (distance > range || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            nearest = candidate;
        }
        return nearest;
    }

    private BlockPos getThrowPoint() {
        return this.side == Side.SOURCE ? this.module.getSourceThrowPoint() : this.module.getDestinationThrowPoint();
    }

    private int findContainerSlot(AbstractContainerMenu menu, Predicate<ItemStack> filter) {
        int containerSlots = this.containerSlotCount(menu);
        for (int slot = 0; slot < containerSlots; slot++) {
            if (filter.test(menu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptyContainerSlot(AbstractContainerMenu menu) {
        int containerSlots = this.containerSlotCount(menu);
        for (int slot = 0; slot < containerSlots; slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private int findInventoryShulkerSlot(AbstractContainerMenu menu) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return -1;
        }
        int containerSlots = this.containerSlotCount(menu);
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            if (!isShulkerBoxStack(player.getInventory().getItem(inventorySlot))) {
                continue;
            }
            int menuSlot = containerSlots + (inventorySlot < 9 ? inventorySlot + 27 : inventorySlot - 9);
            if (menuSlot >= 0 && menuSlot < menu.slots.size()) {
                return menuSlot;
            }
        }
        return -1;
    }

    private int containerSlotCount(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - 36);
    }

    private static boolean isShulkerBoxStack(ItemStack stack) {
        return !stack.isEmpty() && ItemUtils.isShulkerBox(stack.getItem());
    }
}
