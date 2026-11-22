package evernight.client.modules.combat.AntiPhase;

import evernight.client.ClientAccess;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class AntiPhaseLadderPlanner implements ClientAccess, AntiPhasePlacementPlanner {
    private static final List<AntiPhasePlacementShape> ladderShapes;

    static {
        Direction east = Direction.EAST;
        VoxelShape eastShape = LadderBlock.SHAPES.get(Direction.EAST);
        AntiPhasePlacementShape eastPlacement = AntiPhasePlacementShape.fromShape(east, eastShape);
        Direction west = Direction.WEST;
        VoxelShape westShape = LadderBlock.SHAPES.get(Direction.WEST);
        AntiPhasePlacementShape westPlacement = AntiPhasePlacementShape.fromShape(west, westShape);
        Direction north = Direction.NORTH;
        VoxelShape northShape = LadderBlock.SHAPES.get(Direction.NORTH);
        AntiPhasePlacementShape northPlacement = AntiPhasePlacementShape.fromShape(north, northShape);
        Direction south = Direction.SOUTH;
        VoxelShape southShape = LadderBlock.SHAPES.get(Direction.SOUTH);
        AntiPhasePlacementShape southPlacement = AntiPhasePlacementShape.fromShape(south, southShape);
        ladderShapes = List.of(eastPlacement, westPlacement, northPlacement, southPlacement);
    }

    @Override
    public AntiPhasePlacement findPlacement(Player target) {
        BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);
        Direction bestDirection = null;
        AABB bestBounds = null;
        float bestDistanceSquared = 999.0F;
        if (!WorldUtils.isAir(targetPosition)) {
            return null;
        }
        for (AntiPhasePlacementShape shape : ladderShapes) {
            Direction supportDirection = shape.direction();
            BlockPos supportPosition = targetPosition.relative(supportDirection);
            if (!this.shouldRejectPlacement(targetPosition, target, shape)) {
                if (BlockInteractionUtils.isStrictDirectionEnabled()) {
                    if (this.isUnsupportedDirection(supportPosition, supportDirection)) {
                        continue;
                    }
                }
                Vec3 targetPositionVector = target.position();
                Vec3 supportCenter = supportPosition.getCenter();
                float distanceSquared = (float) targetPositionVector.distanceToSqr(supportCenter);
                if (distanceSquared < bestDistanceSquared) {
                    bestBounds = shape.bounds();
                    bestDirection = supportDirection;
                    bestDistanceSquared = distanceSquared;
                }
            }
        }
        if (bestDirection == null) {
            return null;
        }
        return new AntiPhasePlacement(targetPosition.relative(bestDirection), bestDirection.getOpposite(),
                bestBounds.move(targetPosition));
    }

    protected boolean shouldRejectPlacement(BlockPos position, Player target, AntiPhasePlacementShape shape) {
        Direction supportDirection = shape.direction();
        BlockPos supportPosition = position.relative(supportDirection);
        AABB targetBoundsSnapshot = BoundingBoxUtils.getInterpolationBounds(target);
        AABB shapeBounds = shape.bounds();
        AABB worldBounds = shapeBounds.move(position);
        if (!targetBoundsSnapshot.intersects(worldBounds)) {
            BlockPos statePosition = supportPosition;
            BlockState supportState = ClientAccess.evernightMinecraft.level.getBlockState(statePosition);
            return supportState.canBeReplaced();
        }
        return true;
    }

    private boolean isUnsupportedDirection(BlockPos supportPosition, Direction supportDirection) {
        List<Direction> validDirections = BlockInteractionUtils.getStrictDirections(supportPosition);
        return !validDirections.contains(supportDirection.getOpposite());
    }
}