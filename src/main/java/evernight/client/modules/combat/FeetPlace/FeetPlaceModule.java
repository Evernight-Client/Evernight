package evernight.client.modules.combat.FeetPlace;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.world.BlockUpdateAppliedEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.world.WorldStateManager;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PlacementModule;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.BlockerTargetOffsets;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BlockRaycastClassifier;
import evernight.client.utils.combat.BlockRaycastOverride;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.combat.ExtraBreakTarget;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.movement.WallsVelocityHandler;
import evernight.client.utils.world.BlockInteractionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FeetPlaceModule extends PlacementModule {
    private static AntiCheatModule antiCheat;
    private static SpeedMineModule speedMine;

    static {
        antiCheat = ClientContext.getModule(AntiCheatModule.class);
        speedMine = ClientContext.getModule(SpeedMineModule.class);
    }

    private final Setting<Boolean> fireworkSetting;
    private final Setting<SequentialMode> sequentialSetting;
    private final Setting<Boolean> centerSetting;
    private final Setting<Boolean> flattenSetting;
    private final Setting<Boolean> coverSetting;
    private final Setting<Boolean> smartFaceSetting;
    private final Setting<BlockIgnoreMode> blockIgnoreSetting;
    private final Setting<Boolean> onlyObbySetting;
    private final Setting<Boolean> instantSetting;
    private final FeetPlaceFireworkSupport fireworkSupport;
    private boolean recentPositionCorrection;
    private boolean sequentialPlacementComplete;
    private int nearbyCrystalCount;
    private boolean centered;

    public FeetPlaceModule() {
        super("FeetPlace", ModuleCategory.COMBAT, "surround");
        this.placementRotateSetting.setDefaultValue(true);
        this.placementRotateSetting.setValue(true);
        this.fireworkSetting = this.add(new BooleanSetting("Firework", true));
        this.sequentialSetting = this.add(new EnumSetting<>("Sequential", SequentialMode.NONE));
        this.centerSetting = this.add(new BooleanSetting("Center", true));
        this.flattenSetting = this.add(new BooleanSetting("Flatten", true));
        this.coverSetting = this.add(new BooleanSetting("Cover", false));
        this.smartFaceSetting = this.add(new BooleanSetting("SmartFace", false));
        this.blockIgnoreSetting = this.add(new EnumSetting<>("BlockIgnore", BlockIgnoreMode.NONE));
        this.onlyObbySetting = this.add(new BooleanSetting("OnlyObby", false));
        this.instantSetting = this.add(new BooleanSetting("Instant", false));
        this.fireworkSupport = new FeetPlaceFireworkSupport();
        this.setDisableOnRespawn(true);
    }

    public static boolean centerPlayer() {
        if (!ClientAccess.evernightMinecraft.player.onGround()) {
            return false;
        }
        if (ClientContext.holeManager.isInHole()) {
            return false;
        }
        Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.evernightMinecraft.player);
        int occupiedPositionCount = occupiedPositions.size();
        if (occupiedPositionCount <= 4) {
            return false;
        }
        if (WallsVelocityHandler.hasRaisedPlayerCollision()) {
            return false;
        }
        Vec3 originalPosition = ClientAccess.evernightMinecraft.player.position();
        Vec3 centerPosition = new Vec3(Math.floor(ClientAccess.evernightMinecraft.player.getX()) + 0.5D,
                ClientAccess.evernightMinecraft.player.getY(),
                Math.floor(ClientAccess.evernightMinecraft.player.getZ()) + 0.5D);
        Vec3 centerDelta = centerPosition.subtract(originalPosition);
        double distance = centerDelta.length();
        double baseMoveSpeed = MovementPhysics.getBaseMoveSpeed(false);
        double maximumStep = baseMoveSpeed + 0.02D;
        if (distance > maximumStep) {
            double stepFraction = maximumStep / distance;
            Vec3 remainingDelta = centerPosition.subtract(originalPosition);
            Vec3 stepDelta = remainingDelta.scale(stepFraction);
            centerPosition = originalPosition.add(stepDelta);
        }
        ClientAccess.evernightMinecraft.player.setPos(centerPosition);
        double movedDistance = originalPosition.distanceTo(centerPosition);
        return movedDistance <= 0.01D;
    }

    public static void bindReferencedModules(ModuleRegistry modules) {
        antiCheat = modules.getModule(AntiCheatModule.class);
        speedMine = modules.getModule(SpeedMineModule.class);
    }

    @Override
    protected BlockPos getPlacementOrigin() {
        if (!EntityUtils.hasCollisionBelow(ClientAccess.evernightMinecraft.player)) {
            double x = ClientAccess.evernightMinecraft.player.getX();
            double y = ClientAccess.evernightMinecraft.player.getY();
            double z = ClientAccess.evernightMinecraft.player.getZ();
            return BlockPos.containing(x, y, z);
        } else {
            return super.getPlacementOrigin();
        }
    }

    @Override
    protected boolean disableForJumpOrSleep() {
        return !this.recentPositionCorrection && super.disableForJumpOrSleep();
    }

    @Override
    protected boolean shouldDiscardAttackedCrystal() {
        if (!this.instantSetting.getValue()) {
            return RotationManager.isMovementSyncEnabled();
        }
        return true;
    }

    @Override
    protected int getPlacementRotationPriority() {
        return 999;
    }

    @Override
    protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
        if (this.onlyObbySetting.getValue()) {
            Item obsidian = Items.OBSIDIAN;
            return InventoryUtils.findSwapSlot(obsidian, false);
        } else {
            return super.findPlacementSwapSlot();
        }
    }

    @Override
    public void onEnable() {
        this.nearbyCrystalCount = 0;
        if (!this.isWorldUnavailable()) {
            this.centered = false;
            this.countNearbyCrystals();
        }
        super.onEnable();
        this.fireworkSupport.onEnable();
    }

    private void countNearbyCrystals() {
        this.nearbyCrystalCount = 0;
        if (this.isWorldUnavailable()) {
            return;
        }
        Set<BlockPos> occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.evernightMinecraft.player);
        Iterable<Entity> renderEntities = ClientAccess.evernightMinecraft.level.entitiesForRendering();
        Iterator<Entity> entities = renderEntities.iterator();
        while (entities.hasNext()) {
            Entity entity = entities.next();
            if (entity instanceof EndCrystal) {
                BlockPos crystalBase = entity.blockPosition().below();
                if (occupiedPositions.contains(crystalBase)) {
                    this.nearbyCrystalCount++;
                }
            }
        }
    }

    private boolean isIgnoredMiningPosition(BlockPos position) {
        if (!speedMine.isToggled()) {
            return false;
        }
        BlockPos miningPosition = speedMine.getRebreakPos();
        return miningPosition != null && miningPosition.equals(position);
    }

    private boolean isIgnoredBlockPosition(BlockPos position, Set<BlockPos> enemyPositions) {
        return switch (this.blockIgnoreSetting.getValue()) {
            case NONE -> false;
            case MINING -> this.isIgnoredMiningPosition(position);
            case ENEMY -> enemyPositions.contains(position);
            case BOTH -> this.isIgnoredMiningPosition(position) || enemyPositions.contains(position);
        };
    }

    private Set<BlockPos> collectEnemyPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (Player player : ClientAccess.evernightMinecraft.level.players()) {
            if (!player.isAlive() || player == ClientAccess.evernightMinecraft.player
                    || player.distanceTo(ClientAccess.evernightMinecraft.player) > 4.0F
                    || ClientContext.socialRegistry.isFriendPlayer(player)) {
                continue;
            }
            for (BlockPos occupied : PlayerStateUtils.getOccupiedBlockPositions(player)) {
                BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(occupied);
                if (state.isAir() || BlockRaycastClassifier.IGNORE_LOW_RESISTANCE.test(occupied, state) == BlockRaycastOverride.IGNORE) {
                    for (Vec3i offset : BlockerTargetOffsets.SURROUND.getOffsets()) {
                        positions.add(occupied.offset(offset));
                    }
                }
            }
        }
        return positions;
    }

    private int getPlayerHeightInBlocks() {
        double height = ClientAccess.evernightMinecraft.player.getBbHeight();
        return (int) Math.ceil(height);
    }

    private boolean shouldSkipFlattenPosition(BlockPos position) {
        BlockPos placementPosition = ClientContext.worldStateManager.getUnconfirmedBreak();
        if (ClientContext.holeManager.intersectsHole(position)) {
            if (!ClientAccess.evernightMinecraft.player.onGround()) {
                return true;
            }
        }
        if (PlayerStateUtils.isLocalLowProfile()) {
            if (!antiCheat.is2b2tEnabled()) {
                return true;
            }
        }
        if (!position.equals(placementPosition)) {
            if (speedMine.getExtraBreakTarget() != null) {
                ExtraBreakTarget miningAction = speedMine.getExtraBreakTarget();
                return position.equals(miningAction.getPosition());
            }
            return false;
        } else {
            return true;
        }
    }

    private Comparator<BlockPos> getPlacementComparator() {
        double latency = ClientContext.movementSpeedTracker.getCurrentSpeed();
        Comparator<BlockPos> directionComparator;
        if (latency <= 10.0D) {
            Function<BlockPos, Float> playerYawKey = position -> {
                float playerYaw = ClientAccess.evernightMinecraft.player.getYRot();
                BlockPos targetPosition = position;
                float capturedYaw = playerYaw;
                return this.getPlacementPriority(capturedYaw, targetPosition);
            };
            directionComparator = Comparator.comparing(playerYawKey);
        } else {
            Function<BlockPos, Float> movementYawKey = position -> {
                float movementYaw = MovementPhysics.getMovementYaw();
                BlockPos targetPosition = position;
                float capturedYaw = movementYaw;
                return this.getYawPlacementCost(capturedYaw, targetPosition);
            };
            directionComparator = Comparator.comparing(movementYawKey);
        }
        Function<BlockPos, Boolean> pendingPlacementKey = candidate -> {
            WorldStateManager worldState = ClientContext.worldStateManager;
            BlockPos targetPosition = candidate;
            boolean pendingPlacement = worldState.hasPendingPlacement(targetPosition);
            return pendingPlacement;
        };
        Comparator<BlockPos> pendingPlacementComparator = Comparator.comparing(pendingPlacementKey);
        return pendingPlacementComparator.thenComparing(directionComparator);
    }

    @Override
    protected void runPlacementCycle() {
        if (!ClientAccess.evernightMinecraft.player.isPassenger()) {
            ElapsedTimer timer = ClientContext.serverStateManager.getTeleportAckTimer();
            this.recentPositionCorrection = !timer.hasElapsed(100L);
            if (this.recentPositionCorrection) {
                LocalPlayer heightPlayer = ClientAccess.evernightMinecraft.player;
                this.placementBaselineY = heightPlayer.getY();
            }
            try {
                if (this.fireworkSetting.getValue()) {
                    MultiTaskModule multiTask = multiTaskModule;
                    if (!multiTask.isItemUseRestricted()) {
                        Minecraft client = ClientAccess.evernightMinecraft;
                        Runnable processFireworks = () -> {
                            FeetPlaceFireworkSupport support = this.fireworkSupport;
                            Setting<Boolean> placementAirPlaceSettingSnapshot = this.placementAirPlaceSetting;
                            boolean airPlaceEnabled = placementAirPlaceSettingSnapshot.getValue();
                            Setting<Boolean> raytraceSetting = this.placementRaytraceSetting;
                            boolean raytraceEnabled = raytraceSetting.getValue();
                            support.processFireworks(airPlaceEnabled, raytraceEnabled);
                        };
                        client.execute(processFireworks);
                    }
                }
                super.runPlacementCycle();
            } catch (Throwable ignoredFailure) {
            }
        }
    }

    private List<BlockPos> collectPlacementPositions(List<Integer> visitedHashes, BlockPos anchor, int offsetY) {
        InventoryUtils.SwapSlot blockSlot = this.findPlacementSwapSlot();
        Block defaultPlacementBlock = Blocks.OBSIDIAN;
        BlockState placementState = defaultPlacementBlock.defaultBlockState();
        if (blockSlot.isFound()) {
            ItemStack placementStack = blockSlot.getStack();
            if (placementStack.getItem() instanceof BlockItem blockItem) {
                Block placementBlock = blockItem.getBlock();
                placementState = placementBlock.defaultBlockState();
            }
        }
        List<BlockPos> placements = new ArrayList<>();
        int anchorHashCode = anchor.hashCode();
        Integer anchorHash = anchorHashCode;
        visitedHashes.add(anchorHash);
        Iterator<Vec3> offsetIterator = this.getRelativePlacementOffsets(offsetY).iterator();
        while (offsetIterator.hasNext()) {
            Vec3 offset = offsetIterator.next();
            BlockPos blockOffset = BlockPos.containing(offset);
            BlockPos position = anchor.offset(blockOffset);
            if (this.flattenSetting.getValue()) {
                double flattenOffsetY = offset.y;
                if (flattenOffsetY == -1.0D) {
                    if (this.shouldSkipFlattenPosition(position)) {
                        continue;
                    }
                }
            }
            if (this.coverSetting.getValue()) {
                double coverOffsetY = offset.y();
                if (coverOffsetY == this.getPlayerHeightInBlocks()) {
                    if (PlayerStateUtils.isLocalLowProfile()) {
                        if (!RotationManager.is2b2tMode()) {
                            continue;
                        }
                    }
                }
            }
            BlockState collisionState = placementState;
            ClientLevel collisionLevel = ClientAccess.evernightMinecraft.level;
            BlockPos collisionOrigin = BlockPos.ZERO;
            VoxelShape collisionShape = collisionState.getCollisionShape(collisionLevel, collisionOrigin);
            AABB workingBounds;
            if (collisionShape.isEmpty()) {
                workingBounds = new AABB(position);
            } else {
                workingBounds = collisionShape.bounds();
                workingBounds = BoundingBoxUtils.placeAtBlock(workingBounds, position);
            }
            AABB placementBounds = workingBounds;
            this.breakInstantlyMineableBlock(position);
            BlockState positionState = ClientAccess.evernightMinecraft.level.getBlockState(position);
            boolean occupied = !positionState.canBeReplaced();
            if (ClientContext.worldStateManager.hasActiveGlassUpdate(position)) {
                occupied = false;
            }
            if (occupied) {
                workingBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
                AABB positionBounds = new AABB(position);
                if (!BoundingBoxUtils.hasVolumeOverlap(workingBounds, positionBounds)) {
                    continue;
                }
            }
            ClientLevel entityLevel = ClientAccess.evernightMinecraft.level;
            Class<LivingEntity> entityType = LivingEntity.class;
            Predicate<LivingEntity> blocksPlacement = entity -> {
                if (entity != ClientAccess.evernightMinecraft.player) {
                    LivingEntity candidateEntity = entity;
                    AABB entityBounds = candidateEntity.getBoundingBox();
                    return BoundingBoxUtils.hasVolumeOverlap(placementBounds, entityBounds);
                }
                return true;
            };
            List<LivingEntity> blockingEntities = entityLevel.getEntitiesOfClass(entityType, placementBounds, blocksPlacement);
            if (!blockingEntities.isEmpty()) {
                int positionHashCode = position.hashCode();
                Integer positionHash = positionHashCode;
                if (!visitedHashes.contains(positionHash) && offset.y == offsetY) {
                    placements.addAll(this.collectPlacementPositions(visitedHashes, position, offsetY));
                    continue;
                }
            }
            if (!occupied) {
                boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
                Direction placementFace = BlockInteractionUtils.findSupportDirection(position, strictDirection);
                if (placementFace == null) {
                    Setting<Boolean> placementAirPlaceSettingSnapshot = this.placementAirPlaceSetting;
                    if (!placementAirPlaceSettingSnapshot.getValue()) {
                        boolean strictSupportDirection = BlockInteractionUtils.isStrictDirectionEnabled();
                        BlockPos supportPosition = BlockInteractionUtils.findSupportPosition(
                                position, 2, true, strictSupportDirection);
                        if (supportPosition != null) {
                            placements.add(supportPosition);
                        }
                    }
                }
                placements.add(position);
            }
        }
        return placements;
    }

    @Override
    protected void sortPlacementPositions(List positions) {
        positions.sort(this.getPlacementComparator());
    }

    private List<Vec3> getRelativePlacementOffsets(int offsetY) {
        this.countNearbyCrystals();
        List<Vec3> offsets = new ArrayList<>(List.of(new Vec3(-1.0D, offsetY, 0.0),
                new Vec3(1.0D, offsetY, 0.0), new Vec3(0.0, offsetY, 1.0D), new Vec3(0.0, offsetY, -1.0D)));
        if (this.nearbyCrystalCount > 3) {
            if (this.smartFaceSetting.getValue()) {
                Stream<Vec3> offsetStream = offsets.stream();
                Function<Vec3, Vec3> raiseOffset = offset -> {
                    return offset.add(0.0D, 1.0D, 0.0D);
                };
                Stream<Vec3> raisedOffsetStream = offsetStream.map(raiseOffset);
                List<Vec3> raisedOffsets = raisedOffsetStream.toList();
                offsets.addAll(raisedOffsets);
            }
        }
        if (this.flattenSetting.getValue()) {
            Vec3 flattenOffset = new Vec3(0.0, offsetY - 1, 0.0);
            offsets.add(flattenOffset);
        }
        if (this.coverSetting.getValue()) {
            Vec3 coverOffset = new Vec3(0.0, offsetY + this.getPlayerHeightInBlocks(), 0.0);
            offsets.add(coverOffset);
        }
        return offsets;
    }

    private float getPlacementPriority(float yaw, BlockPos position) {
        int positionY = position.getY();
        BlockPos playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
        if (positionY == playerBlockPosition.getY()) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            if (this.hasNontrivialSurroundCollision(player)) {
                BlockPos distanceOrigin = PlayerStateUtils.getLocalRoundedBlockPosition();
                double targetX = position.getX();
                double targetY = position.getY();
                double targetZ = position.getZ();
                return (float) distanceOrigin.distToLowCornerSqr(targetX, targetY, targetZ);
            }
        }
        float capturedYaw = yaw;
        return this.getYawPlacementCost(capturedYaw, position);
    }

    @EventHandler
    private void runSequentialPlacement(BlockUpdateAppliedEvent event) {
        if (this.sequentialSetting.getValue() != SequentialMode.NONE
                && (this.sequentialSetting.getValue() == SequentialMode.STRONG || !this.sequentialPlacementComplete)) {
            this.runPlacementCycle();
            this.sequentialPlacementComplete = this.hasPlacedThisCycle();
        }
    }

    @EventHandler
    private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
        this.sequentialPlacementComplete = false;
        if (!this.centered) {
            if (this.centerSetting.getValue()) {
                if (!ClientAccess.evernightMinecraft.player.isPassenger()) {
                    this.centered = centerPlayer();
                }
            }
        }
    }

    @Override
    public List getPlacementPositions() {
        Boolean fireworkValue = this.fireworkSetting.getValue();
        Set<BlockPos> trackedPositions;
        if (fireworkValue) {
            trackedPositions = EntityUtils.getFireworkPositions();
        } else {
            trackedPositions = Collections.emptySet();
        }
        Set<BlockPos> fireworkPositions = trackedPositions;
        List<Integer> visitedHashes = new ArrayList<>();
        BlockPos placementOrigin = this.getPlacementOrigin();
        List<BlockPos> positions = this.collectPlacementPositions(visitedHashes, placementOrigin, 0);
        Set<BlockPos> enemyPositions = this.blockIgnoreSetting.getValue() == BlockIgnoreMode.ENEMY
                || this.blockIgnoreSetting.getValue() == BlockIgnoreMode.BOTH ? this.collectEnemyPositions() : Collections.emptySet();
        Predicate<BlockPos> discardPosition = position -> {
            if (!this.isIgnoredBlockPosition(position, enemyPositions)) {
                if (this.fireworkSetting.getValue()) {
                    return this.fireworkSupport.isTrackedFireworkPosition(fireworkPositions, position);
                }
                return false;
            } else {
                return true;
            }
        };
        positions.removeIf(discardPosition);
        return positions;
    }

    @EventHandler
    private void observeSurroundPackets(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket) {
            Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
            PositionMoveRotation packetChange = positionPacket.change();
            Vec3 packetPosition = packetChange.position();
            double distanceSquared = playerPosition.distanceToSqr(packetPosition);
            if (distanceSquared > 9.0D) {
                PositionMoveRotation freshPacketChange = positionPacket.change();
                this.placementBaselineY = freshPacketChange.position().y;
                ElapsedTimer correctionTimer = ClientContext.serverStateManager.getTeleportAckTimer();
                correctionTimer.reset();
            }
        }
        if (this.fireworkSetting.getValue()) {
            this.fireworkSupport.observeSpawnPacket(event);
        }
    }

    private float getYawPlacementCost(float yaw, BlockPos position) {
        Vec3 targetCenter = position.getCenter();
        float targetYaw = RotationManager.getRotationsToPosition(targetCenter)[0];
        float yawDifference = Mth.degreesDifferenceAbs(yaw, targetYaw);
        float height = position.getY();
        return yawDifference + height * 0.001F;
    }

    private boolean hasNontrivialSurroundCollision(LivingEntity entity) {
        Set<BlockPos> occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(entity);
        Iterator<BlockPos> positions = occupiedPositions.iterator();
        while (positions.hasNext()) {
            BlockPos position = positions.next();
            BlockState blockState = ClientAccess.evernightMinecraft.level.getBlockState(position);
            ClientLevel collisionLevel = ClientAccess.evernightMinecraft.level;
            BlockPos collisionOrigin = BlockPos.ZERO;
            VoxelShape shape = blockState.getCollisionShape(collisionLevel, collisionOrigin);
            if (!shape.isEmpty()) {
                AABB bounds = shape.bounds();
                if (!BoundingBoxUtils.hasUnitDimensions(bounds)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean shouldHandleCrystalSpawn() {
        if (RotationManager.isMovementSyncEnabled()) {
            return this.instantSetting.getValue();
        } else {
            return ClientContext.crystalInteractionManager.isInstantAttackEnabled();
        }
    }

    private enum BlockIgnoreMode implements NamedEntry {
        NONE("None"), MINING("Mining"), ENEMY("Enemy"), BOTH("Both");
        private final String displayName;

        BlockIgnoreMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    private enum SequentialMode implements NamedEntry {
        NONE("None"),
        STRICT("Strict"),
        STRONG("Strong");
        private final String displayName;

        SequentialMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}