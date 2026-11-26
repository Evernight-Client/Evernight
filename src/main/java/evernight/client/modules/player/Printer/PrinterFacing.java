package evernight.client.modules.player.Printer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Facing/orientation helpers for the printer.
 *
 * <p>Vanilla decides the placement state of orientable blocks from the click face and the player's
 * look direction. Instead of hard-coding every block's rules, the printer runs the real
 * {@link BlockPlaceContext} placement hook on the client and keeps only the click sides whose
 * simulated state matches the projection.
 */
public final class PrinterFacing {
    private static final double FACE_EPSILON = 1.0E-4D;

    private PrinterFacing() {
    }

    /**
     * @return the direction this state points at, or {@code null} when it has no orientation property
     */
    public static @Nullable Direction getFacing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            return state.getValue(BlockStateProperties.FACING_HOPPER);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return switch (state.getValue(BlockStateProperties.AXIS)) {
                case X -> Direction.EAST;
                case Y -> Direction.UP;
                case Z -> Direction.SOUTH;
            };
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            return switch (state.getValue(BlockStateProperties.HORIZONTAL_AXIS)) {
                case X -> Direction.EAST;
                case Z -> Direction.SOUTH;
                default -> null;
            };
        }
        return null;
    }

    /**
     * Whether the block's placement direction is special enough that the printer should look
     * straight at the projected facing instead of only aiming at the clicked face.
     */
    public static boolean isRedstoneComponent(BlockState state) {
        // Order matters only for readability; every check is on the block class so modded states
        // extending a vanilla class are covered too.
        return state.getBlock() instanceof RedStoneWireBlock
                || state.getBlock() instanceof DiodeBlock
                || state.getBlock() instanceof BasePressurePlateBlock
                || state.getBlock() instanceof ObserverBlock
                || state.getBlock() instanceof TargetBlock
                || state.getBlock() instanceof TripWireHookBlock
                || state.getBlock() instanceof DaylightDetectorBlock
                || state.getBlock() instanceof PistonBaseBlock
                || state.getBlock() instanceof RedstoneLampBlock
                || state.getBlock() instanceof AbstractFurnaceBlock;
    }

    public static boolean isObserver(BlockState state) {
        return state.getBlock() instanceof ObserverBlock;
    }

    /**
     * @return whether the projected state has a placement-controlled orientation/half property
     */
    public static boolean hasPlacementOrientation(BlockState state) {
        return getFacing(state) != null
                || state.hasProperty(BlockStateProperties.HALF)
                || state.hasProperty(BlockStateProperties.HANGING)
                || state.hasProperty(BlockStateProperties.ATTACH_FACE)
                || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART)
                || state.hasProperty(SlabBlock.TYPE)
                || state.hasProperty(BlockStateProperties.AXIS)
                || state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS);
    }

    public static boolean isTopHalf(BlockState state) {
        if (state.hasProperty(SlabBlock.TYPE)) {
            return state.getValue(SlabBlock.TYPE) == SlabType.TOP;
        }
        return state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.TOP;
    }

    public static boolean isBottomHalf(BlockState state) {
        if (state.hasProperty(SlabBlock.TYPE)) {
            return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        }
        return state.hasProperty(BlockStateProperties.HALF)
                && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }

    /**
     * Whether the state produced by the placement hook can be accepted for the projected state.
     *
     * <p>Only properties the player can choose while placing are compared. Things the world computes
     * afterwards (stair shape, redstone connections, waterlogging, powered flags) must not make the
     * printer reject an otherwise correct placement.
     */
    public static boolean matches(BlockState target, BlockState simulated) {
        if (target.getBlock() != simulated.getBlock()) {
            return false;
        }
        return matches(target, simulated, BlockStateProperties.FACING)
                && matches(target, simulated, BlockStateProperties.HORIZONTAL_FACING)
                && matches(target, simulated, BlockStateProperties.FACING_HOPPER)
                && matches(target, simulated, BlockStateProperties.AXIS)
                && matches(target, simulated, BlockStateProperties.HORIZONTAL_AXIS)
                && matches(target, simulated, BlockStateProperties.HALF)
                && matches(target, simulated, BlockStateProperties.HANGING)
                && matches(target, simulated, BlockStateProperties.ATTACH_FACE)
                && matches(target, simulated, BlockStateProperties.DOUBLE_BLOCK_HALF)
                && matches(target, simulated, BlockStateProperties.BED_PART)
                && matches(target, simulated, SlabBlock.TYPE);
    }

    private static <T extends Comparable<T>> boolean matches(
            BlockState target, BlockState simulated, Property<T> property) {
        if (!target.hasProperty(property)) {
            return true;
        }
        if (!simulated.hasProperty(property)) {
            return false;
        }
        return target.getValue(property).equals(simulated.getValue(property));
    }

    /**
     * Runs the vanilla placement hook for {@code target} as if the player clicked {@code hit}.
     *
     * @param aim look direction used to resolve orientable blocks; {@code null} uses the player's
     *            current rotation
     * @return the state vanilla would create, or {@code null} when the block returns none
     */
    public static @Nullable BlockState simulate(
            BlockState target, Level level, Player player, InteractionHand hand, ItemStack stack,
            BlockHitResult hit, @Nullable Vec3 aim) {
        // Do not require a BlockItem here: redstone dust / string place wire and tripwire, and wall
        // variants (wall torch, wall sign, ...) are placed from the base item instead of the block.
        BlockPlaceContext context = aim == null
                ? new BlockPlaceContext(player, hand, stack, hit)
                : new SimulatedPlaceContext(level, player, hand, stack, hit, aim);
        return target.getBlock().getStateForPlacement(context);
    }

    /**
     * @return yaw/pitch (in that order) that makes the player look along {@code direction}
     */
    public static float[] rotationsForDirection(Direction direction) {
        return switch (direction) {
            case UP -> new float[]{0.0F, -90.0F};
            case DOWN -> new float[]{0.0F, 90.0F};
            default -> new float[]{direction.toYRot(), 0.0F};
        };
    }

    /**
     * Hit location on the clickable face. A half-block is placed as top/bottom depending on the click
     * height, so the aim point has to sit near the matching edge instead of the face center.
     *
     * @return the world-space hit position, or {@code null} when this side cannot produce the
     * requested half
     */
    public static @Nullable Vec3 hitPosition(BlockPos position, Direction side, BlockState target) {
        double x = position.getX() + 0.5D;
        double z = position.getZ() + 0.5D;
        boolean top = isTopHalf(target);
        boolean bottom = isBottomHalf(target);
        if (side == Direction.UP) {
            if (bottom) {
                return null;
            }
            return new Vec3(x, position.getY() + 1.0D - FACE_EPSILON, z);
        }
        if (side == Direction.DOWN) {
            if (top) {
                return null;
            }
            return new Vec3(x, position.getY() + FACE_EPSILON, z);
        }
        double y = top ? position.getY() + 1.0D - 0.01D
                : bottom ? position.getY() + 0.01D
                : position.getY() + 0.5D;
        return new Vec3(x + side.getStepX() * 0.5D, y, z + side.getStepZ() * 0.5D);
    }

    /**
     * Sorts all six directions by how closely they match an aim vector; the first entry is the
     * direction vanilla's nearest-looking helpers would pick.
     */
    private static Direction[] sortByAim(Vec3 aim) {
        Direction[] directions = Direction.values();
        Vec3 normalized = aim.lengthSqr() < 1.0E-8D ? Vec3.ZERO : aim.normalize();
        Arrays.sort(directions, Comparator.comparingDouble((Direction direction) -> {
            double dot = direction.getStepX() * normalized.x
                    + direction.getStepY() * normalized.y
                    + direction.getStepZ() * normalized.z;
            return -dot;
        }));
        return directions;
    }

    /**
     * Placement context that answers looking-direction queries from a synthetic aim vector instead
     * of mutating the real player's rotation during simulation.
     */
    private static final class SimulatedPlaceContext extends BlockPlaceContext {
        private final Direction horizontalDirection;
        private final Direction[] lookingDirections;

        private SimulatedPlaceContext(
                Level level, Player player, InteractionHand hand, ItemStack stack,
                BlockHitResult hit, Vec3 aim) {
            super(level, player, hand, stack, hit);
            this.lookingDirections = sortByAim(aim);
            this.horizontalDirection = Direction.getApproximateNearest(aim.x, 0.0D, aim.z);
        }

        @Override
        public Direction getHorizontalDirection() {
            // The super constructor queries canBeReplaced before our fields are assigned.
            return this.horizontalDirection == null ? Direction.NORTH : this.horizontalDirection;
        }

        @Override
        public Direction getNearestLookingDirection() {
            return this.lookingDirections == null ? Direction.NORTH : this.lookingDirections[0];
        }

        @Override
        public Direction[] getNearestLookingDirections() {
            if (this.lookingDirections == null) {
                return Direction.values();
            }
            Direction[] directions = this.lookingDirections.clone();
            if (!this.replacingClickedOnBlock()) {
                Direction front = this.getClickedFace().getOpposite();
                int index = 0;
                while (index < directions.length && directions[index] != front) {
                    index++;
                }
                if (index > 0 && index < directions.length) {
                    System.arraycopy(directions, 0, directions, 1, index);
                    directions[0] = front;
                }
            }
            return directions;
        }

        @Override
        public float getRotation() {
            return this.horizontalDirection.toYRot();
        }
    }
}
