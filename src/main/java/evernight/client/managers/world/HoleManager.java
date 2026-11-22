package evernight.client.managers.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.settings.options.DimensionType;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.world.BlockStateUtils;
import evernight.client.utils.world.HoleInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public final class HoleManager implements ClientAccess {
    private static final Direction[] horizontalDirections;
    private static final ScheduledExecutorService scanExecutor;
    private static final MutableBlockPos classificationCursor;
    private static final int SCAN_DIAMETER = 32;
    private static final int SCAN_BUDGET_PER_TICK = 512;

    static {
        Plane horizontalPlane = Plane.HORIZONTAL;
        horizontalDirections = horizontalPlane.stream().toArray(Direction[]::new);
        scanExecutor = Executors.newSingleThreadScheduledExecutor();
        classificationCursor = new MutableBlockPos();
    }

    private volatile List<HoleInfo> holes = new ArrayList<>();
    private boolean inHole;
    private BlockPos scanOrigin;
    private List<HoleInfo> scanAccumulator;
    private Set<Long> scanCovered;
    private int scanStep;

    public HoleManager() {
        ClientAccess.eventBus.subscribe(this);
    }

    static HoleInfo classifyHole(BlockPos position, Direction fromDirection) {
        classificationCursor.set(position);
        MutableBlockPos cursorSnapshot = classificationCursor;
        if (BlockStateUtils.isBlockingExceptCarpet(cursorSnapshot)) {
            return null;
        }
        Direction down = Direction.DOWN;
        classificationCursor.move(down);
        MutableBlockPos floorPosition = classificationCursor;
        BlockState floorState = ClientAccess.evernightMinecraft.level.getBlockState(floorPosition);
        if (!BlockStateUtils.isBlocking(floorState)) {
            return null;
        }
        if (floorState.getBlock() instanceof Portal) {
            return null;
        }
        if (floorState.getBlock() instanceof LiquidBlock) {
            return null;
        }
        cursorSnapshot = classificationCursor;
        Direction up = Direction.UP;
        cursorSnapshot.move(up, 2);
        cursorSnapshot = classificationCursor;
        if (BlockStateUtils.isBlocking(cursorSnapshot)) {
            return null;
        }
        Direction roofDirection = Direction.UP;
        MutableBlockPos roofCursor = classificationCursor;
        cursorSnapshot = roofCursor.move(roofDirection);
        boolean trapped = BlockStateUtils.isBlocking(cursorSnapshot);
        HoleInfo.Safety safety = BlockStateUtils.isBlastResistantUnbreakable(floorState)
                ? HoleInfo.Safety.SAFE : HoleInfo.Safety.UNSAFE;
        Direction doubleDirection = null;
        for (Direction direction : horizontalDirections) {
            if (fromDirection == direction.getOpposite()) {
                continue;
            }
            MutableBlockPos sideResetCursor = classificationCursor;
            cursorSnapshot = sideResetCursor.set(position);
            cursorSnapshot.move(direction);
            MutableBlockPos sidePosition = classificationCursor;
            BlockState sideState = ClientAccess.evernightMinecraft.level.getBlockState(sidePosition);
            cursorSnapshot = classificationCursor;
            if (!BlockStateUtils.isBlocking(cursorSnapshot)) {
                if (doubleDirection != null || fromDirection != null) {
                    return null;
                }
                BlockPos adjacentBlock = position.relative(direction);
                HoleInfo adjacentHole = classifyHole(adjacentBlock, direction);
                if (adjacentHole == null) {
                    return null;
                }
                if (!adjacentHole.trapped()) {
                    trapped = false;
                }
                if (safety == HoleInfo.Safety.SAFE) {
                    safety = adjacentHole.safety();
                }
                doubleDirection = direction;
                continue;
            }
            Block anchorBlock = Blocks.RESPAWN_ANCHOR;
            if (sideState.is(anchorBlock)) {
                DimensionType anchorPolicy = ClientContext.worldStateManager.getDimension();
                if (!anchorPolicy.isNether()) {
                    return null;
                }
            }
            if (BlockStateUtils.isWeakOrInstantlyBreakable(sideState)) {
                return null;
            }
            if (BlockStateUtils.isBlastResistantBreakable(sideState)) {
                safety = HoleInfo.Safety.UNSAFE;
            }
        }
        return new HoleInfo(safety, position.immutable(), createHoleBounds(position, doubleDirection), trapped);
    }

    private static AABB createHoleBounds(BlockPos position, Direction face) {
        if (face == null) return new AABB(position);
        AABB box = new AABB(position);
        Vec3i xVector = face.getUnitVec3i();
        double x = xVector.getX();
        Vec3i zVector = face.getUnitVec3i();
        double z = zVector.getZ();
        return box.expandTowards(x, 0.0, z);
    }

    public boolean intersectsHole(BlockPos position) {
        return this.intersectsHole(new AABB(position));
    }

    public boolean intersectsHole(AABB bounds) {
        return this.holes.stream().anyMatch(hole -> {
                    AABB holeBounds = hole.bounds();
                    return holeBounds.intersects(bounds);
                }
        );
    }

    public List getHoles() {
        return this.holes;
    }

    public void setHoles(List updatedHoles) {
        this.holes = updatedHoles;
    }

    public HoleInfo findIntersectingHole(BlockPos position) {
        Stream<HoleInfo> matchingHoles = this.holes.stream().filter(hole -> {
                    AABB holeBounds = hole.bounds();
                    return holeBounds.intersects(new AABB(position));
                }
        );
        Optional<HoleInfo> firstMatch = matchingHoles.findFirst();
        return firstMatch.orElse(null);
    }

    public boolean isInHole() {
        return this.inHole;
    }

    private void runScanSlice() {
        if (this.isWorldUnavailable()) {
            this.scanOrigin = null;
            return;
        }
        BlockPos playerPosition = ClientAccess.evernightMinecraft.player.blockPosition();
        if (this.scanOrigin != null && this.scanOrigin.distSqr(playerPosition) > 64.0D) {
            this.scanOrigin = null;
        }
        if (this.scanOrigin == null) {
            this.scanOrigin = playerPosition.immutable();
            this.scanAccumulator = new ArrayList<>();
            this.scanCovered = new HashSet<>();
            this.scanStep = 0;
        }
        int processed = 0;
        int total = SCAN_DIAMETER * SCAN_DIAMETER * SCAN_DIAMETER;
        while (this.scanStep < total && processed < SCAN_BUDGET_PER_TICK) {
            int index = this.scanStep++;
            int xOffset = index % SCAN_DIAMETER - SCAN_DIAMETER / 2;
            int zOffset = (index / SCAN_DIAMETER) % SCAN_DIAMETER - SCAN_DIAMETER / 2;
            int yOffset = index / (SCAN_DIAMETER * SCAN_DIAMETER) - SCAN_DIAMETER / 2;
            BlockPos candidate = this.scanOrigin.offset(xOffset, yOffset, zOffset);
            if (!this.scanCovered.contains(candidate.asLong())) {
                HoleInfo hole = classifyHole(candidate, null);
                if (hole != null) {
                    this.scanAccumulator.add(hole);
                    AABB bounds = hole.bounds();
                    for (int x = (int) bounds.minX; x < (int) bounds.maxX; x++) {
                        for (int z = (int) bounds.minZ; z < (int) bounds.maxZ; z++) {
                            int y = (int) bounds.minY;
                            this.scanCovered.add(BlockPos.asLong(x, y, z));
                            this.scanCovered.add(BlockPos.asLong(x, y + 1, z));
                            this.scanCovered.add(BlockPos.asLong(x, y + 2, z));
                        }
                    }
                }
            }
            processed++;
        }
        if (this.scanStep >= total) {
            this.setHoles(this.scanAccumulator);
            this.scanOrigin = null;
        }
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.runScanSlice();
        BlockPos roundedPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
        this.inHole = this.intersectsHole(roundedPosition);
    }
}