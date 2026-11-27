package evernight.client.modules.auto.PearlBot;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * State machine behind {@link PearlBotModule}.
 *
 * <p>The module only reacts while {@link Phase#IDLE}, so a request arriving while the bot is already
 * walking, clicking or returning is dropped instead of interrupting the sequence. Every phase is
 * driven from {@link #tick()} and the network side is limited to the same use-item packets a player
 * would send.
 *
 * <p>The interaction itself is sent from {@link #tick()} after {@link #onSendPosition(
 * LocalPlayerSendPositionEvent)} has written the target rotation into this tick's movement packet, so
 * the server receives "turn toward the trigger" before "use the trigger".
 */
public final class PearlBotController implements ClientAccess {
    /** One tick of pure rotation before the first click, so the press never looks instantaneous. */
    private static final int SETTLE_TICKS = 1;
    /** How often Baritone is asked again when it stopped without putting the player in reach. */
    private static final long REPATH_INTERVAL_MILLIS = 500L;
    private static final long WALK_TIMEOUT_MILLIS = 30_000L;
    private static final long RETURN_TIMEOUT_MILLIS = 30_000L;
    private static final int MAX_PATH_ATTEMPTS = 6;
    /** Vanilla interaction distance is three blocks; keep a small margin. */
    private static final double INTERACT_REACH = 2.9D;
    private static final double RETURN_TOLERANCE = 1.0D;
    /** Horizontal radius used to decide whether a pearl entity hangs below a certain sign. */
    private static final double PEARL_SEARCH_RADIUS = 3.0D;
    private static final int ROTATION_PRIORITY = 100;
    private static final Pattern NAME_SEPARATOR = Pattern.compile("[^A-Za-z0-9_]+");
    private static final int[][] HORIZONTAL_OFFSETS =
            {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1};

    private enum Phase {
        IDLE,
        WALK_TO_TARGET,
        INTERACT,
        RETURN
    }

    /**
     * The button or trapdoor below a matching sign.
     *
     * @param triggerPosition button or trapdoor directly below the sign
     * @param trapdoor        whether the trigger is a trapdoor (needs two clicks)
     */
    private record StasisTarget(BlockPos triggerPosition, boolean trapdoor) {
    }

    private final PearlBotModule module;
    private final ElapsedTimer walkTimer;
    private final ElapsedTimer returnTimer;
    private final ElapsedTimer repathTimer;

    private Phase phase;
    private String sender;
    private StasisTarget target;
    private BlockPos standPosition;
    private Vec3 originPosition;
    private ClientLevel lastLevel;
    private boolean pathActive;
    private int pathAttempts;
    private int settleTicks;
    private int clickDelayTicks;
    private int clicksSent;

    public PearlBotController(PearlBotModule module) {
        this.module = module;
        this.walkTimer = new ElapsedTimer();
        this.returnTimer = new ElapsedTimer();
        this.repathTimer = new ElapsedTimer();
        this.phase = Phase.IDLE;
        this.lastLevel = evernightMinecraft.level;
    }

    public String getPhaseName() {
        return this.phase.name();
    }

    public void reset() {
        this.abort();
        this.lastLevel = evernightMinecraft.level;
    }

    public void shutdown() {
        this.abort();
    }

    /**
     * Starts a sequence for one private message. Ignored unless the bot is idle and ready.
     */
    public void request(String incomingSender) {
        if (this.phase != Phase.IDLE || this.isWorldUnavailable() || incomingSender == null) {
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (player == null || player.isDeadOrDying() || player.isSpectator()) {
            return;
        }
        Set<String> names = identifyNames(incomingSender);
        if (names.isEmpty()) {
            return;
        }
        String localName = evernightMinecraft.getUser().getName();
        if (localName != null && names.contains(localName.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (ClientContext.baritoneBridge == null || !ClientContext.baritoneBridge.isAvailable()) {
            this.module.notifyError("NoBaritone");
            return;
        }
        // The displayed sender may carry a rank prefix; the whisperable name is the last name token.
        String replyName = replyNameOf(incomingSender);
        StasisTarget foundTarget = this.findTarget(names);
        if (foundTarget == null) {
            this.module.reply(replyName, this.module.getNotFoundMessage());
            return;
        }
        this.module.reply(replyName, this.module.getFoundMessage());
        this.module.reply(replyName, this.module.getComingMessage());
        this.sender = replyName;
        this.target = foundTarget;
        this.originPosition = player.position();
        this.standPosition = this.findStandPosition(foundTarget.triggerPosition());
        if (this.isWithinReach()) {
            this.enterInteract();
        } else {
            this.startWalking(1);
        }
    }

    public void tick() {
        if (this.isWorldUnavailable()) {
            this.abort();
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        ClientLevel level = evernightMinecraft.level;
        if (player == null || level == null || player.isDeadOrDying() || player.isSpectator()) {
            this.abort();
            return;
        }
        if (level != this.lastLevel) {
            this.abort();
            this.lastLevel = level;
            return;
        }
        switch (this.phase) {
            case IDLE -> {
            }
            case WALK_TO_TARGET -> this.tickWalk();
            case INTERACT -> this.tickInteract();
            case RETURN -> this.tickReturn();
        }
    }

    /**
     * Writes the rotation toward the trigger into this tick's movement packet while interacting.
     */
    public void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (event.getStage() != EventStage.Pre || this.phase != Phase.INTERACT || this.isWorldUnavailable()) {
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return;
        }
        BlockHitResult hitResult = this.createHitResult();
        if (hitResult == null) {
            return;
        }
        float[] angles = RotationManager.calculateRotations(player.getEyePosition(), hitResult.getLocation());
        event.setRotation(angles);
        ClientContext.rotationManager.requestRotation(angles, ROTATION_PRIORITY);
    }

    private void startWalking(int attempts) {
        this.cancelPathing();
        this.phase = Phase.WALK_TO_TARGET;
        this.pathAttempts = attempts;
        this.settleTicks = 0;
        this.clickDelayTicks = 0;
        this.clicksSent = 0;
        this.walkTimer.reset();
        this.repathTimer.reset();
        this.pathToTarget();
    }

    private void enterInteract() {
        this.cancelPathing();
        this.phase = Phase.INTERACT;
        this.settleTicks = SETTLE_TICKS;
        this.clickDelayTicks = 0;
        this.clicksSent = 0;
    }

    private void tickWalk() {
        if (this.target == null) {
            this.abort();
            return;
        }
        if (this.isWithinReach()) {
            this.enterInteract();
            return;
        }
        if (this.walkTimer.hasElapsed(WALK_TIMEOUT_MILLIS) || this.pathAttempts > MAX_PATH_ATTEMPTS) {
            this.module.notifyError("WalkTimeout");
            this.abort();
            return;
        }
        if (!this.isPathing() && this.repathTimer.hasElapsedAndReset(REPATH_INTERVAL_MILLIS)) {
            this.pathAttempts++;
            this.pathToTarget();
        }
    }

    private void tickInteract() {
        if (this.target == null) {
            this.abort();
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return;
        }
        BlockPos triggerPosition = this.target.triggerPosition();
        if (!this.isTriggerBlock(triggerPosition)) {
            this.module.notifyError("TriggerMissing");
            this.abort();
            return;
        }
        BlockHitResult hitResult = this.createHitResult();
        if (hitResult == null || !this.isWithinReach()) {
            this.resumeWalking();
            return;
        }
        if (this.settleTicks > 0) {
            this.settleTicks--;
            return;
        }
        if (this.clickDelayTicks > 0) {
            this.clickDelayTicks--;
            return;
        }
        // Belt and braces: the tick's movement packet already carried this rotation (see
        // onSendPosition), but an overlapping rotation source could have overwritten it, so send the
        // look again immediately before the interaction.
        float[] angles = RotationManager.calculateRotations(player.getEyePosition(), hitResult.getLocation());
        PacketSender.sendRotation(angles, player.onGround());
        PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hitResult);
        PacketSender.sendSwing(InteractionHand.MAIN_HAND);
        this.clicksSent++;
        if (this.target.trapdoor() && this.clicksSent == 1) {
            // The counter is decremented once per tick before the next click, so delay - 1 keeps the
            // two presses exactly the configured number of ticks apart.
            this.clickDelayTicks = this.module.getTrapdoorDelay() - 1;
            return;
        }
        this.module.reply(this.sender, this.module.getDoneMessage());
        this.finishTrigger();
    }

    private void tickReturn() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return;
        }
        if (this.originPosition != null && horizontalDistance(player.position(), this.originPosition) <= RETURN_TOLERANCE) {
            this.abort();
            return;
        }
        if (this.returnTimer.hasElapsed(RETURN_TIMEOUT_MILLIS) || this.pathAttempts > MAX_PATH_ATTEMPTS) {
            this.module.notifyError("ReturnTimeout");
            this.abort();
            return;
        }
        if (!this.isPathing() && this.repathTimer.hasElapsedAndReset(REPATH_INTERVAL_MILLIS)) {
            this.pathAttempts++;
            this.pathToOrigin();
        }
    }

    private void finishTrigger() {
        this.settleTicks = 0;
        this.clickDelayTicks = 0;
        this.clicksSent = 0;
        if (!this.module.isReturnHome() || this.originPosition == null) {
            this.abort();
            return;
        }
        this.cancelPathing();
        this.phase = Phase.RETURN;
        this.pathAttempts = 0;
        this.returnTimer.reset();
        this.repathTimer.reset();
        this.pathToOrigin();
    }

    private void resumeWalking() {
        if (this.pathAttempts >= MAX_PATH_ATTEMPTS) {
            this.module.notifyError("WalkTimeout");
            this.abort();
            return;
        }
        this.startWalking(this.pathAttempts + 1);
    }

    private void abort() {
        this.cancelPathing();
        this.phase = Phase.IDLE;
        this.sender = null;
        this.target = null;
        this.standPosition = null;
        this.originPosition = null;
        this.pathAttempts = 0;
        this.settleTicks = 0;
        this.clickDelayTicks = 0;
        this.clicksSent = 0;
    }

    private void pathToTarget() {
        if (this.target == null || ClientContext.baritoneBridge == null) {
            return;
        }
        BlockPos triggerPosition = this.target.triggerPosition();
        if (this.standPosition != null) {
            // GoalBlock means "stand on top of this block", so aim at the floor below the feet block.
            ClientContext.baritoneBridge.pathTo(this.standPosition.below());
        } else {
            ClientContext.baritoneBridge.pathToXZ(triggerPosition.getX(), triggerPosition.getZ());
        }
        this.pathActive = true;
    }

    private void pathToOrigin() {
        if (this.originPosition == null || ClientContext.baritoneBridge == null) {
            return;
        }
        BlockPos supportBlock = this.supportBlock(this.originPosition);
        if (supportBlock != null) {
            ClientContext.baritoneBridge.pathTo(supportBlock);
        } else {
            BlockPos originBlock = BlockPos.containing(
                    this.originPosition.x, this.originPosition.y, this.originPosition.z);
            ClientContext.baritoneBridge.pathToXZ(originBlock.getX(), originBlock.getZ());
        }
        this.pathActive = true;
    }

    /**
     * @return the block the player was standing on, or null when the feet were over a gap
     */
    private BlockPos supportBlock(Vec3 feetPosition) {
        ClientLevel level = evernightMinecraft.level;
        if (level == null) {
            return null;
        }
        BlockPos candidate = BlockPos.containing(feetPosition.x, feetPosition.y - 0.05D, feetPosition.z);
        return level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty() ? null : candidate;
    }

    private boolean isPathing() {
        return ClientContext.baritoneBridge != null && ClientContext.baritoneBridge.isPathingOrCalculating();
    }

    private void cancelPathing() {
        if (this.pathActive && ClientContext.baritoneBridge != null) {
            ClientContext.baritoneBridge.cancelPathing();
        }
        this.pathActive = false;
    }

    private boolean isWithinReach() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null || this.target == null) {
            return false;
        }
        Vec3 triggerCenter = this.target.triggerPosition().getCenter();
        return player.getEyePosition().distanceTo(triggerCenter) <= INTERACT_REACH;
    }

    private BlockHitResult createHitResult() {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null || this.target == null) {
            return null;
        }
        BlockPos triggerPosition = this.target.triggerPosition();
        Vec3 center = triggerPosition.getCenter();
        Direction face = faceTowards(center, player.getEyePosition());
        double offsetX = face.getStepX() * 0.5D;
        double offsetY = face.getStepY() * 0.5D;
        double offsetZ = face.getStepZ() * 0.5D;
        Vec3 hitPoint = center.add(offsetX, offsetY, offsetZ);
        return new BlockHitResult(hitPoint, face, triggerPosition, false);
    }

    private boolean isTriggerBlock(BlockPos position) {
        ClientLevel level = evernightMinecraft.level;
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        return state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    /**
     * Picks the sign to trigger. Signs with an ender pearl hanging below them win, because those
     * chambers actually hold a pearl to pull; among equally good signs the closest one wins.
     */
    private StasisTarget findTarget(Set<String> names) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return null;
        }
        Vec3 position = player.position();
        double range = this.module.getScanRange();
        StasisTarget bestWithPearl = null;
        double bestPearlDistance = Double.MAX_VALUE;
        StasisTarget nearestWithoutPearl = null;
        double nearestDistance = Double.MAX_VALUE;
        List<BlockEntity> blockEntities = WorldUtils.getLoadedBlockEntities();
        for (BlockEntity blockEntity : blockEntities) {
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                continue;
            }
            BlockPos signPosition = sign.getBlockPos();
            double distance = position.distanceTo(signPosition.getCenter());
            if (distance > range) {
                continue;
            }
            if (!matchesNames(sign, names)) {
                continue;
            }
            BlockPos triggerPosition = this.findTrigger(signPosition);
            if (triggerPosition == null) {
                continue;
            }
            StasisTarget candidate = new StasisTarget(triggerPosition, this.isTrapdoor(triggerPosition));
            if (this.hasPearlBelow(signPosition)) {
                if (distance < bestPearlDistance) {
                    bestPearlDistance = distance;
                    bestWithPearl = candidate;
                }
            } else if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestWithoutPearl = candidate;
            }
        }
        return bestWithPearl != null ? bestWithPearl : nearestWithoutPearl;
    }

    /**
     * @return whether an ender pearl entity hangs below the given sign: within
     * {@value #PEARL_SEARCH_RADIUS} blocks horizontally and at or below the sign's Y level
     */
    private boolean hasPearlBelow(BlockPos signPosition) {
        ClientLevel level = evernightMinecraft.level;
        if (level == null) {
            return false;
        }
        Vec3 center = signPosition.getCenter();
        AABB searchBox = new AABB(
                center.x - PEARL_SEARCH_RADIUS,
                level.getMinY(),
                center.z - PEARL_SEARCH_RADIUS,
                center.x + PEARL_SEARCH_RADIUS,
                signPosition.getY() + 1.0D,
                center.z + PEARL_SEARCH_RADIUS);
        return !level.getEntitiesOfClass(ThrownEnderpearl.class, searchBox).isEmpty();
    }

    private BlockPos findTrigger(BlockPos signPosition) {
        for (int depth = 1; depth <= 2; depth++) {
            BlockPos candidate = signPosition.below(depth);
            if (this.isTriggerBlock(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isTrapdoor(BlockPos position) {
        ClientLevel level = evernightMinecraft.level;
        if (level == null) {
            return false;
        }
        return level.getBlockState(position).getBlock() instanceof TrapDoorBlock;
    }

    /**
     * Finds a nearby block a player can stand on to reach the trigger, preferring the closest one.
     * Standing on top of a trapdoor is excluded, because opening it would drop the player.
     */
    private BlockPos findStandPosition(BlockPos triggerPosition) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return null;
        }
        boolean trapdoor = this.isTrapdoor(triggerPosition);
        Vec3 playerPosition = player.position();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int verticalOffset : VERTICAL_OFFSETS) {
            for (int[] horizontalOffset : HORIZONTAL_OFFSETS) {
                BlockPos standCandidate = triggerPosition.offset(
                        horizontalOffset[0], verticalOffset, horizontalOffset[1]);
                if (standCandidate.equals(triggerPosition)) {
                    continue;
                }
                if (trapdoor && standCandidate.equals(triggerPosition.above())) {
                    continue;
                }
                if (!this.isStandable(standCandidate)) {
                    continue;
                }
                double distance = playerPosition.distanceTo(standCandidate.getCenter());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = standCandidate;
                }
            }
        }
        return best;
    }

    private boolean isStandable(BlockPos position) {
        ClientLevel level = evernightMinecraft.level;
        if (level == null) {
            return false;
        }
        if (!level.getBlockState(position).isAir() || !level.getBlockState(position.above()).isAir()) {
            return false;
        }
        BlockPos floor = position.below();
        return !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
    }

    private static Set<String> identifyNames(String sender) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (sender == null) {
            return names;
        }
        String trimmed = sender.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return names;
        }
        names.add(trimmed);
        for (String token : NAME_SEPARATOR.split(trimmed)) {
            if (token.length() >= 3 && token.length() <= 16) {
                names.add(token);
            }
        }
        return names;
    }

    private static String replyNameOf(String sender) {
        String trimmed = sender.trim();
        String[] tokens = NAME_SEPARATOR.split(trimmed);
        for (int index = tokens.length - 1; index >= 0; index--) {
            String token = tokens[index];
            if (token.length() >= 3 && token.length() <= 16) {
                return token;
            }
        }
        return trimmed;
    }

    private static boolean matchesNames(SignBlockEntity sign, Set<String> names) {
        String text = signText(sign);
        for (String name : names) {
            if (text.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static String signText(SignBlockEntity sign) {
        StringBuilder builder = new StringBuilder();
        appendSignText(sign, true, builder);
        appendSignText(sign, false, builder);
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendSignText(SignBlockEntity sign, boolean front, StringBuilder builder) {
        for (Component line : sign.getText(front).getMessages(false)) {
            builder.append(line.getString()).append('\n');
        }
    }

    private static Direction faceTowards(Vec3 center, Vec3 eye) {
        double deltaX = eye.x - center.x;
        double deltaY = eye.y - center.y;
        double deltaZ = eye.z - center.z;
        double absoluteX = Math.abs(deltaX);
        double absoluteY = Math.abs(deltaY);
        double absoluteZ = Math.abs(deltaZ);
        if (absoluteX >= absoluteY && absoluteX >= absoluteZ) {
            return deltaX >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (absoluteZ >= absoluteY) {
            return deltaZ >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
        return deltaY >= 0.0D ? Direction.UP : Direction.DOWN;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }
}
