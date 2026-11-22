package evernight.client.modules.movement.FastFall;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.world.HoleManager;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.exploit.Phase.PhaseModule;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.Vec3Utils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.mixin.minecraft.world.BlockBehaviourAccessor;
import evernight.mixin.minecraft.entity.LocalPlayerAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.StepHeightEvent;
import evernight.client.modules.movement.HoleSnap.HoleSnapModule;
import evernight.client.modules.movement.Speed.SpeedModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class FastFallModule extends Module {
    private static SpeedModule speedModule;
    private static HoleSnapModule holeSnapModule;
    private static PhaseModule phaseModule;
    final Setting<ModeChoice> modeSetting;
    private final Setting<Float> heightSetting;
    private final Setting<Float> speedSetting;
    private final Setting<Boolean> stopInLiquidsSetting;
    private final Setting<Boolean> onlyHoleSetting;
    private final Setting<Boolean> safeDisableSetting;
    private final ElapsedTimer strictPulseTimer;
    public boolean suppressSprint;
    private boolean strictTimerArmed;
    private boolean strictTimerPulse;
    private boolean holeBelow;
    private boolean acceleratingFall;
    private boolean hasFastFallen;
    private boolean steppedThisTick;
    private double collisionBlockYOffset;
    private double groundDistance;

    public FastFallModule() {
        super("FastFall", ModuleCategory.MOVEMENT, "reversestep");
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PLAIN));
        this.heightSetting = this.add(new NumberSetting<>("Height", 2.0F, 1.0F, 15.0F).withUnit("m"));
        Predicate<Object> showSpeed = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
        this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.0F, 5.0F, showSpeed));
        this.stopInLiquidsSetting = this.add(new BooleanSetting("StopInLiquids", true));
        this.onlyHoleSetting = this.add(new BooleanSetting("OnlyHole", false));
        this.safeDisableSetting = this.add(new BooleanSetting("SafeDisable", false));
        this.strictPulseTimer = new ElapsedTimer();
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        speedModule = registry.getModule(SpeedModule.class);
        holeSnapModule = registry.getModule(HoleSnapModule.class);
        phaseModule = registry.getModule(PhaseModule.class);
    }

    private boolean canUseStrictTimer() {
        if (!this.canFastFall() || !this.isStrictMode()
                || this.collisionBlockYOffset == 0.0 || this.groundDistance == 0.0) {
            return false;
        }
        double currentGroundDistance = this.groundDistance;
        Float maximumHeight = this.heightSetting.getValue();
        return currentGroundDistance <= maximumHeight.floatValue();
    }

    private void releaseStrictTimer() {
        if (this.isStrictMode()) {
            this.strictTimerArmed = false;
            ClientContext.playerTickController.releaseMultiplier(this);
        }
    }

    private boolean isUnsupportedAtEdge() {
        AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        Vec3 center = bounds.getCenter();
        double minX = bounds.minX;
        double minZ = bounds.minZ;
        double maxX = bounds.maxX;
        double maxZ = bounds.maxZ;
        HashMap<Vec3, Vec3> rayEnds = new HashMap<>();
        rayEnds.put(center, new Vec3(center.x, center.y - 1.0D, center.z));
        rayEnds.put(new Vec3(minX, center.y, minZ), new Vec3(minX, center.y - 1.0D, minZ));
        rayEnds.put(new Vec3(maxX, center.y, minZ), new Vec3(maxX, center.y - 1.0D, minZ));
        rayEnds.put(new Vec3(minX, center.y, maxZ), new Vec3(minX, center.y - 1.0D, maxZ));
        rayEnds.put(new Vec3(maxX, center.y, maxZ), new Vec3(maxX, center.y - 1.0D, maxZ));
        Iterator<Vec3> rayStarts = rayEnds.keySet().iterator();
        while (rayStarts.hasNext()) {
            Vec3 rayStart = rayStarts.next();
            BlockHitResult hit = ClientAccess.evernightMinecraft.level.clip(new ClipContext(rayStart,
                    rayEnds.get(rayStart), Block.COLLIDER, Fluid.NONE, ClientAccess.evernightMinecraft.player));
            if (hit != null) {
                if (hit.getType() == Type.BLOCK) {
                    return false;
                }
            }
        }
        if (!this.hasCollidableBlockAhead()) {
            return false;
        }
        ClientLevel belowLevelSnapshot = ClientAccess.evernightMinecraft.level;
        double playerX = ClientAccess.evernightMinecraft.player.getX();
        double playerY = ClientAccess.evernightMinecraft.player.getY();
        double belowPlayerY = playerY - 1.0D;
        double belowZ = ClientAccess.evernightMinecraft.player.getZ();
        BlockPos belowPos = BlockPos.containing(playerX, belowPlayerY, belowZ);
        BlockState stateAtFeet = belowLevelSnapshot.getBlockState(belowPos);
        if (stateAtFeet != null) {
            return stateAtFeet.getBlock() == Blocks.AIR;
        }
        return true;
    }

    private boolean hasCollidableBlockAhead() {
        Vec3 velocity = ClientAccess.evernightMinecraft.player.getDeltaMovement();
        double xDirection = velocity.x;
        double zDirection = velocity.z;
        double absoluteZ = Math.abs(zDirection);
        double absoluteX = Math.abs(xDirection);
        xDirection = absoluteZ > absoluteX ? 0.0 : Math.signum(xDirection);
        double projectedAbsoluteX = Math.abs(xDirection);
        double originalAbsoluteZ = Math.abs(zDirection);
        zDirection = projectedAbsoluteX > originalAbsoluteZ ? 0.0 : Math.signum(zDirection);
        double truncatedY = (int) ClientAccess.evernightMinecraft.player.getY();
        double fractionY = ClientAccess.evernightMinecraft.player.getY() - truncatedY;
        double scanY = truncatedY + Math.signum(fractionY);
        ClientLevel levelSnapshot = ClientAccess.evernightMinecraft.level;
        double aheadXSnapshot = ClientAccess.evernightMinecraft.player.getX() + xDirection;
        double aheadYSnapshot = scanY - 1.0D;
        double aheadZ = ClientAccess.evernightMinecraft.player.getZ() + zDirection;
        BlockPos aheadPos = BlockPos.containing(aheadXSnapshot, aheadYSnapshot, aheadZ);
        BlockState aheadState = levelSnapshot.getBlockState(aheadPos);
        if (aheadState != null) {
            return ((BlockBehaviourAccessor) aheadState.getBlock()).isCollidable();
        } else {
            return true;
        }
    }

    private boolean canFastFall() {
        if (speedModule.isToggled()) {
            if (speedModule.modeSetting.getValue() != SpeedModule.ModeChoice.OnGround) {
                if (speedModule.modeSetting.getValue() != SpeedModule.ModeChoice.Vanilla) {
                    return false;
                }
            }
        }
        if (ClientAccess.evernightMinecraft.player.isShiftKeyDown()) {
            return false;
        }
        if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
            return false;
        }
        if (ClientAccess.evernightMinecraft.player.isInWall()) {
            return false;
        }
        if (PlayerStateUtils.isInLiquid(ClientAccess.evernightMinecraft.player)) {
            if (this.stopInLiquidsSetting.getValue()) {
                return false;
            }
        }
        if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
            return false;
        }
        ClientLevel levelSnapshot = ClientAccess.evernightMinecraft.level;
        BlockPos feetPos = BlockPos.containing(ClientAccess.evernightMinecraft.player.position());
        BlockState feetState = levelSnapshot.getBlockState(feetPos);
        return !(feetState.getBlock() instanceof BedBlock);
    }

    @Override
    public void onToggle() {
        this.suppressSprint = false;
        this.hasFastFallen = false;
    }

    private boolean isStrictMode() {
        return this.modeSetting.getValue() == FastFallModule.ModeChoice.STRICT;
    }

    @EventHandler
    private void onMove(LocalPlayerMoveEvent event) {
        this.collisionBlockYOffset = RaycastUtils.getCollisionBlockYOffset();
        this.groundDistance = RaycastUtils.getGroundDistance();
        HoleManager holeManagerSnapshot = ClientContext.holeManager;
        BlockPos position = ClientAccess.evernightMinecraft.player.blockPosition();
        int blocksBelow = (int) this.groundDistance;
        BlockPos targetPos = position.below(blocksBelow);
        this.holeBelow = holeManagerSnapshot.intersectsHole(targetPos);
        if (!this.holeBelow) {
            if (this.onlyHoleSetting.getValue()) {
                return;
            }
        }
        double horizontalFactor = 0.3D;
        boolean hasHoleSnapTarget = false;
        if (holeSnapModule.isToggled()) {
            if (holeSnapModule.targetTimeoutTimer.hasElapsed(500L)) {
                hasHoleSnapTarget = holeSnapModule.getSnapTarget() != null;
            }
        }
        boolean strictGroundedFall = false;
        if (this.isStrictMode() && this.canFastFall() && this.isUnsupportedAtEdge()) {
            strictGroundedFall = ClientAccess.evernightMinecraft.player.onGround();
        }
        if (!strictGroundedFall) {
            if (!this.acceleratingFall || hasHoleSnapTarget) {
                return;
            }
            if (!this.hasCollidableBlockAhead()) {
                return;
            }
        }
        Vec3 movementSnapshot = event.getVelocity();
        double scaledXSnapshot = event.getX() * horizontalFactor;
        double eventY = event.getY();
        double scaledZ = event.getZ() * horizontalFactor;
        Vec3Utils.setComponents(movementSnapshot, scaledXSnapshot, eventY, scaledZ);
    }

    @EventHandler
    private void onStepHeight(StepHeightEvent event) {
        if (event.getStage() == EventStage.Post) {
            if (ClientAccess.evernightMinecraft.player.onGround()) {
                double height = event.getHeight();
                if (height > 0.6D) {
                    this.steppedThisTick = true;
                }
            }
        }
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        boolean movementAllowed = MovementPhysics.isLocalPlayerMoving();
        if (!movementAllowed) {
            if (holeSnapModule.isToggled()) {
                movementAllowed = holeSnapModule.getSnapTarget() != null;
            }
        }
        if (!this.holeBelow) {
            if (this.onlyHoleSetting.getValue()) {
                return;
            }
        }
        if (!movementAllowed) {
            return;
        }
        if (this.steppedThisTick) {
            this.steppedThisTick = false;
            return;
        }
        this.acceleratingFall = false;
        if (!((LocalPlayerAccessor) ClientAccess.evernightMinecraft.player).lastOnGround()) {
            return;
        }
        Vec3 currentVelocity = ClientAccess.evernightMinecraft.player.getDeltaMovement();
        if (!(currentVelocity.y() <= 0.0)) {
            return;
        }
        if (!this.canFastFall()) {
            return;
        }
        if (this.isStrictMode()) {
            return;
        }
        double playerY = ClientAccess.evernightMinecraft.player.getY();
        AABB boundsSnapshot = ClientAccess.evernightMinecraft.player.getBoundingBox();
        Float maximumHeight = this.heightSetting.getValue();
        double negativeHeight = -maximumHeight;
        AABB scanBounds = boundsSnapshot.expandTowards(0.0, negativeHeight, 0.0).setMaxY(playerY);
        if (ClientAccess.evernightMinecraft.level.noCollision(ClientAccess.evernightMinecraft.player, scanBounds)
                && !ClientAccess.evernightMinecraft.level.containsAnyLiquid(scanBounds)) {
            return;
        }
        Iterable<VoxelShape> collisions = ClientAccess.evernightMinecraft.level.getBlockCollisions(ClientAccess.evernightMinecraft.player, scanBounds);
        AtomicReference<Double> highestCollisionY = new AtomicReference<>(
                (double) ClientAccess.evernightMinecraft.level.getMinY());
        collisions.forEach(shape -> {
            double previousHighestSnapshot = highestCollisionY.get();
            Axis verticalAxis = Axis.Y;
            double shapeTopY = shape.max(verticalAxis);
            double newHighestY = Math.max(previousHighestSnapshot, shapeTopY);
            highestCollisionY.set(newHighestY);
        });
        double highestY = highestCollisionY.get();
        if (!(highestY < ClientAccess.evernightMinecraft.player.getY())) {
            return;
        }
        boolean limitDescent = true;
        double destinationY = highestCollisionY.get();
        Vec3 velocity = ClientAccess.evernightMinecraft.player.getDeltaMovement();
        double destinationXSnapshot = ClientAccess.evernightMinecraft.player.getX();
        double destinationZ = ClientAccess.evernightMinecraft.player.getZ();
        BlockPos destinationPos = BlockPos.containing(destinationXSnapshot, destinationY, destinationZ);
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        Vec3i velocityOffset = new Vec3i((int) velocity.x, -1, (int) velocity.z);
        if (!level.isEmptyBlock(destinationPos.offset(velocityOffset))) {
            if (ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(25L)) {
                limitDescent = this.modeSetting.getValue() == FastFallModule.ModeChoice.PLAIN;
            }
        }
        double dropDistance = ClientAccess.evernightMinecraft.player.getY() - destinationY;
        if (dropDistance > 2.0D || limitDescent) {
            double limitY = ClientAccess.evernightMinecraft.player.getY();
            destinationY = limitY - 2.0D;
            this.acceleratingFall = true;
            Setting<ModeChoice> speedMode = this.modeSetting;
            float selectedSpeed;
            if (speedMode.getValue() == FastFallModule.ModeChoice.PLAIN) {
                Float configuredSpeed = this.speedSetting.getValue();
                selectedSpeed = configuredSpeed;
            } else {
                selectedSpeed = 1.0F;
            }
            if (selectedSpeed != 0.0F) {
                LocalPlayer velocityReceiver = ClientAccess.evernightMinecraft.player;
                double downwardSpeed = -selectedSpeed;
                Axis verticalAxis = Axis.Y;
                Vec3 acceleratedVelocity = velocity.with(verticalAxis, downwardSpeed);
                velocityReceiver.setDeltaMovement(acceleratedVelocity);
            }
        }
        if (!limitDescent) {
            LocalPlayer positionTarget = ClientAccess.evernightMinecraft.player;
            double positionXSnapshot = positionTarget.getX();
            double positionZ = ClientAccess.evernightMinecraft.player.getZ();
            positionTarget.setPos(positionXSnapshot, destinationY, positionZ);
        }
        phaseModule.resetGroundedTicks();
        this.hasFastFallen = true;
    }

    @EventHandler
    private void onStrictTimerTick(LocalPlayerPreTickEvent event) {
        if (this.safeDisableSetting.getValue()) {
            if (ClientContext.holeManager.isInHole() && this.hasFastFallen) {
                this.disable();
                return;
            }
        }
        if (!this.canUseStrictTimer() || (!this.holeBelow && this.onlyHoleSetting.getValue())) {
            this.releaseStrictTimer();
            return;
        }
        if (!ClientAccess.evernightMinecraft.player.onGround()) {
            ElapsedTimer pulseTimerSnapshot = this.strictPulseTimer;
            if (pulseTimerSnapshot.hasElapsed(500L)) {
                this.strictTimerPulse = !this.strictTimerPulse;
                pulseTimerSnapshot = this.strictPulseTimer;
                pulseTimerSnapshot.reset();
            }
            if (ClientAccess.evernightMinecraft.player.getDeltaMovement().y < 0.0 && this.strictTimerArmed
                    && this.strictTimerPulse) {
                ClientContext.playerTickController.requestMultiplier(this, 2.0F);
                this.hasFastFallen = true;
            } else {
                this.releaseStrictTimer();
            }
        } else {
            this.strictTimerArmed = true;
            ClientContext.playerTickController.releaseMultiplier(this);
            int groundCounter = ClientContext.playerStateTracker.getGroundedTicks();
            if (groundCounter > 3) {
                this.strictPulseTimer.reset();
                this.strictTimerPulse = true;
            }
        }
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        STRICT("Strict"),
        INSTANT("Instant");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}