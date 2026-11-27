package evernight.client.modules.auto.AutoTransport;

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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * State machine behind {@link AutoTransportModule}.
 *
 * <p>Each tick advances exactly one phase. Container phases open the marked chest or the nearest
 * pre-placed ender chest, quick-move shulker boxes and close the screen again; the pearl phases
 * refill from the marked pearl chest, sneak to the rim of the nearest bubble column and throw down
 * into it. The machine is purely client side: it only sends the same clicks and use-item packets a
 * player would send.
 *
 * <p>A position jump larger than the configured teleport distance is treated as an arrival on every
 * phase, because the partner may trigger the pearl before the local player has finished loading.
 *
 * <p>While the transport owns a Baritone path it switches block breaking and placing off, so the walk
 * to a chest never digs through or builds over the base.
 */
public final class AutoTransportController implements ClientAccess {
    private static final int PHASE_TIMEOUT_TICKS = 600;
    private static final int OPEN_ATTEMPT_MILLIS = 500;
    private static final int MAX_OPEN_ATTEMPTS = 20;
    private static final int PAUSE_RETRY_MILLIS = 1000;
    private static final int CLOSE_GRACE_MILLIS = 1000;
    private static final int THROW_AIM_MILLIS = 100;
    private static final int FAILURE_REPEAT_MILLIS = 10_000;
    private static final int BUBBLE_SEARCH_MILLIS = 500;
    private static final int MIN_CONTAINER_MENU_SLOTS = 27 + 36;
    private static final int REQUIRED_PEARLS = 16;
    /**
     * Horizontal distance from the bubble column centre at which the walk stops. The player box is
     * 0.6 wide, so at 0.95 the box edge stays outside the 1x1 column block while hugging its rim.
     */
    private static final double EDGE_STOP_DISTANCE = 0.95D;
    private static final int[][] EDGE_OFFSETS =
            {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /**
     * The item frame anchors: redstone dust named 搬走 marks the source end, redstone block named
     * 搬到 marks the destination end. The names are item names configured by the player, not UI text.
     */
    private static final Item SOURCE_ANCHOR_ITEM = Items.REDSTONE;
    private static final String SOURCE_ANCHOR_NAME = "搬走";
    private static final Item DESTINATION_ANCHOR_ITEM = Items.REDSTONE_BLOCK;
    private static final String DESTINATION_ANCHOR_NAME = "搬到";

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
    private BlockPos bubbleColumn;
    private Vec3 lastTickPosition;
    private ClientLevel lastLevel;
    private int phaseTicks;
    private int openAttempts;
    private boolean moduleScreenOpen;
    private boolean closingScreen;
    private boolean openRequested;
    private boolean pathActive;
    private boolean pathingOnlyHeld;
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
        this.releaseMovement();
        this.phase = AutoTransportPhase.WAIT_RECALL;
        this.pausedPhase = AutoTransportPhase.WAIT_RECALL;
        this.target = null;
        this.bubbleColumn = null;
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
        this.releaseMovement();
        this.phase = nextPhase;
        this.phaseTicks = 0;
        this.target = null;
        this.bubbleColumn = null;
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
        Side detected = this.resolveSideFromAnchors();
        return detected != null ? detected : Side.SOURCE;
    }

    private Side resolveArrivalSide() {
        if (this.module.getSideChoice() != AutoTransportSideChoice.AUTO) {
            return this.side;
        }
        Side detected = this.resolveSideFromAnchors();
        if (detected != null) {
            return detected;
        }
        return this.side == Side.SOURCE ? Side.DESTINATION : Side.SOURCE;
    }

    /**
     * Detects the current end from the named item frame anchors around the player.
     *
     * <p>The two ends are far apart, so at most one end's anchor is within the scan radius. When both
     * are somehow visible the nearest one wins.
     *
     * @return the detected side, or null when no anchor frame is loaded near the player
     */
    private Side resolveSideFromAnchors() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return null;
        }
        double range = this.module.getScanRange();
        Optional<ItemFrame> sourceAnchor = ItemFrameContainerLocator.findNearestFrame(
                stack -> hasAnchorName(stack, SOURCE_ANCHOR_ITEM, SOURCE_ANCHOR_NAME), range);
        Optional<ItemFrame> destinationAnchor = ItemFrameContainerLocator.findNearestFrame(
                stack -> hasAnchorName(stack, DESTINATION_ANCHOR_ITEM, DESTINATION_ANCHOR_NAME), range);
        if (sourceAnchor.isPresent() && destinationAnchor.isPresent()) {
            Vec3 position = player.position();
            double sourceDistance = position.distanceToSqr(sourceAnchor.get().position());
            double destinationDistance = position.distanceToSqr(destinationAnchor.get().position());
            return sourceDistance <= destinationDistance ? Side.SOURCE : Side.DESTINATION;
        }
        if (sourceAnchor.isPresent()) {
            return Side.SOURCE;
        }
        return destinationAnchor.isPresent() ? Side.DESTINATION : null;
    }

    private static boolean hasAnchorName(ItemStack stack, Item item, String name) {
        if (stack.isEmpty() || !stack.is(item)) {
            return false;
        }
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && name.equals(customName.getString().trim());
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
        if (this.module.getSideChoice() == AutoTransportSideChoice.AUTO) {
            Side detected = this.resolveSideFromAnchors();
            if (detected != null) {
                this.side = detected;
            }
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
        if (this.bubbleColumn == null) {
            if (!this.scanTimer.hasElapsedAndReset(BUBBLE_SEARCH_MILLIS)) {
                return;
            }
            this.bubbleColumn = this.findBubbleColumnTop();
            if (this.bubbleColumn == null) {
                this.pause("NoBubbleColumn");
                return;
            }
        }
        BlockPos column = this.bubbleColumn;
        Vec3 columnCenter = column.getCenter();
        double horizontalDistance =
                Math.hypot(player.getX() - columnCenter.x, player.getZ() - columnCenter.z);
        if (!this.isInsideBubbleColumn(column) && horizontalDistance > EDGE_STOP_DISTANCE) {
            if (this.findEdgeStandSpot(column) == null) {
                this.pause("NoBubbleEdge");
                return;
            }
            this.moveTowardColumnEdge(column);
            this.checkPhaseTimeout();
            return;
        }
        this.holdSneakInPlace();
        float[] angles = RotationManager.calculateRotations(player.getEyePosition(), columnCenter);
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

    /**
     * Finds the top block of the nearest bubble column around the player's feet level.
     *
     * <p>Columns are grouped by their horizontal position and only the highest block inside the
     * vertical band is kept, so a column is represented by its surface block. The nearest column wins,
     * with a vertical offset penalty to prefer the level the player is standing on.
     */
    private BlockPos findBubbleColumnTop() {
        LocalPlayer player = evernightMinecraft.player;
        ClientLevel level = evernightMinecraft.level;
        if (player == null || level == null) {
            return null;
        }
        BlockPos origin = player.blockPosition();
        int radius = (int) Math.ceil(this.module.getBubbleRadius());
        int minimumY = origin.getY() - 2;
        int maximumY = origin.getY() + 1;
        Map<Long, Integer> topYByColumn = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).is(Blocks.BUBBLE_COLUMN)) {
                        continue;
                    }
                    long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
                    Integer previous = topYByColumn.get(key);
                    if (previous == null || y > previous) {
                        topYByColumn.put(key, y);
                    }
                }
            }
        }
        BlockPos nearest = null;
        double bestScore = Double.MAX_VALUE;
        for (Map.Entry<Long, Integer> entry : topYByColumn.entrySet()) {
            int columnX = (int) (entry.getKey() >> 32);
            int columnZ = (int) entry.getKey().longValue();
            int columnY = entry.getValue();
            double horizontal =
                    Math.hypot(columnX + 0.5D - player.getX(), columnZ + 0.5D - player.getZ());
            if (horizontal > radius) {
                continue;
            }
            double vertical = Math.abs(columnY - origin.getY());
            double score = horizontal + vertical * 3.0D;
            if (score < bestScore) {
                bestScore = score;
                nearest = new BlockPos(columnX, columnY, columnZ);
            }
        }
        return nearest;
    }

    /**
     * @return the closest walkable block next to the column at the player's feet level, or null when
     * every neighbour is blocked or water
     */
    private BlockPos findEdgeStandSpot(BlockPos column) {
        ClientLevel level = evernightMinecraft.level;
        LocalPlayer player = evernightMinecraft.player;
        if (level == null || player == null) {
            return null;
        }
        int standY = player.blockPosition().getY();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int[] offset : EDGE_OFFSETS) {
            BlockPos stand = new BlockPos(column.getX() + offset[0], standY, column.getZ() + offset[1]);
            if (!level.getBlockState(stand).isAir() || !level.getBlockState(stand.above()).isAir()) {
                continue;
            }
            BlockPos floor = stand.below();
            if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                continue;
            }
            double distance = player.position().distanceTo(stand.getCenter());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = stand;
            }
        }
        return best;
    }

    /**
     * Walks toward the rim of the column while sneaking, using world-relative movement input so the
     * camera direction does not matter. Movement is released once the player box hugs the rim.
     */
    private void moveTowardColumnEdge(BlockPos column) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return;
        }
        Vec3 columnCenter = column.getCenter();
        double offsetX = player.getX() - columnCenter.x;
        double offsetZ = player.getZ() - columnCenter.z;
        double offsetLength = Math.hypot(offsetX, offsetZ);
        double normalX = offsetLength > 1.0E-4D ? offsetX / offsetLength : 1.0D;
        double normalZ = offsetLength > 1.0E-4D ? offsetZ / offsetLength : 0.0D;
        double edgeX = columnCenter.x + normalX * 0.5D;
        double edgeZ = columnCenter.z + normalZ * 0.5D;
        double deltaX = edgeX - player.getX();
        double deltaZ = edgeZ - player.getZ();
        double distance = Math.hypot(deltaX, deltaZ);
        if (distance < 0.05D) {
            this.releaseMovement();
            return;
        }
        double directionX = deltaX / distance;
        double directionZ = deltaZ / distance;
        float yawRadians = (float) Math.toRadians(player.getYRot());
        float sin = (float) Math.sin(yawRadians);
        float cos = (float) Math.cos(yawRadians);
        float forward = (float) (directionX * -sin + directionZ * cos);
        float strafe = (float) (directionX * cos + directionZ * sin);
        float scale = (float) Math.min(1.0D, distance / 0.8D);
        ClientContext.inputOverrides.requestSneak(true);
        ClientContext.inputOverrides.requestSprint(false);
        ClientContext.inputOverrides.requestForwardMovement(forward * scale);
        ClientContext.inputOverrides.requestSidewaysMovement(strafe * scale);
    }

    private boolean isInsideBubbleColumn(BlockPos column) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return false;
        }
        BlockPos feet = player.blockPosition();
        return feet.getX() == column.getX() && feet.getZ() == column.getZ() && feet.getY() == column.getY();
    }

    private void releaseMovement() {
        ClientContext.inputOverrides.requestForwardMovement(0.0F);
        ClientContext.inputOverrides.requestSidewaysMovement(0.0F);
        ClientContext.inputOverrides.requestSneak(false);
        ClientContext.inputOverrides.requestSprint(false);
    }

    /**
     * Keeps the player crouched on the rim while the throw is being aimed.
     */
    private void holdSneakInPlace() {
        ClientContext.inputOverrides.requestForwardMovement(0.0F);
        ClientContext.inputOverrides.requestSidewaysMovement(0.0F);
        ClientContext.inputOverrides.requestSneak(true);
        ClientContext.inputOverrides.requestSprint(false);
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
                this.holdPathingOnly();
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
        this.releasePathingOnly();
    }

    /**
     * Keeps Baritone from digging or placing while the transport owns the path; paired with
     * {@link #releasePathingOnly()}.
     */
    private void holdPathingOnly() {
        if (this.pathingOnlyHeld || ClientContext.baritoneBridge == null) {
            return;
        }
        ClientContext.baritoneBridge.requestNoBreakPlace();
        this.pathingOnlyHeld = true;
    }

    private void releasePathingOnly() {
        if (!this.pathingOnlyHeld) {
            return;
        }
        this.pathingOnlyHeld = false;
        if (ClientContext.baritoneBridge != null) {
            ClientContext.baritoneBridge.releaseNoBreakPlace();
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
