package evernight.client.utils.world;

import evernight.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placement geometry for the storage chain of the automatic mining modules.
 *
 * <p>Two search modes are supported:
 * <ul>
 *     <li>{@link #findSafeSpot(int)} finds a solid 5x5x5 area and is used when the caller carves a
 *     3x3x3 pocket into it (AutoMining).</li>
 *     <li>{@link #findSurfaceSpot(BlockPos, int)} finds an already existing 3x3x3 space on the
 *     surface and performs no terrain modification at all (AutoSand).</li>
 * </ul>
 */
public final class StoragePlacement implements ClientAccess {
    /**
     * Radius of the sphere search for a carvable storage spot.
     */
    public static final int SAFE_SPOT_RADIUS = 32;
    /**
     * Radius of the cube search for an existing surface storage space.
     */
    public static final int SURFACE_SPOT_RADIUS = 16;
    /**
     * Player has to be within this squared distance (1.5 blocks) of the stand position to place.
     */
    public static final double STAND_DISTANCE_SQR = 2.25D;
    /**
     * Player has to be within this squared distance (2 blocks) of an existing surface spot.
     */
    public static final double SURFACE_STAND_DISTANCE_SQR = 4.0D;

    private StoragePlacement() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * Searches outwards shell by shell for a position surrounded by solid blocks, so a 3x3x3 pocket
     * can be carved and used as a stable storage platform.
     *
     * @param minY lowest Y a storage spot may be placed at
     * @return the safe spot, or null when nothing was found within {@link #SAFE_SPOT_RADIUS}
     */
    public static BlockPos findSafeSpot(int minY) {
        if (evernightMinecraft.player == null || evernightMinecraft.level == null) {
            return null;
        }
        BlockPos playerPos = evernightMinecraft.player.blockPosition();
        for (int radius = 1; radius <= SAFE_SPOT_RADIUS; radius++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) < radius && Math.abs(dy) < radius && Math.abs(dz) < radius) {
                            continue;
                        }
                        BlockPos candidate = playerPos.offset(dx, dy, dz);
                        if (!isSafeSpotCandidate(candidate.getY() - 2, minY, isSolidArea(candidate))) {
                            continue;
                        }
                        double distance = playerPos.distSqr(candidate);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * Finds an already existing 3x3x3 space around the player without breaking any terrain.
     *
     * <p>The player has to be able to stand in the center (two air blocks above a stable floor), and
     * the whole 3x3 footprint has to be clear so the container can be placed next to the player.
     * Positions at the player's own Y level are preferred.
     *
     * @param origin search center, usually {@code player.blockPosition()}
     * @param radius horizontal search radius
     * @return the feet position of the space, or null when none exists nearby
     */
    public static BlockPos findSurfaceSpot(BlockPos origin, int radius) {
        if (evernightMinecraft.player == null || evernightMinecraft.level == null) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    BlockPos feet = origin.offset(dx, dy, dz);
                    if (!hasClearVolume(feet, 0, 2)) {
                        continue;
                    }
                    if (!hasClearVolume(feet, 1, 2)) {
                        continue;
                    }
                    double distance = feet.distSqr(origin) + Math.abs(dy) * 4.0D;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = feet;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Checks an existing air volume: a stable floor below every column and no fluid/falling block in
     * the space.
     *
     * @param feet            feet position of the center column
     * @param horizontalRadius radius around the center column (1 = 3x3)
     * @param height          number of air blocks above the floor
     * @return whether the whole volume is clear
     */
    public static boolean hasClearVolume(BlockPos feet, int horizontalRadius, int height) {
        if (evernightMinecraft.level == null) {
            return false;
        }
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                BlockPos floorPos = feet.offset(dx, -1, dz);
                BlockState floorState = evernightMinecraft.level.getBlockState(floorPos);
                if (floorState.isAir() || !floorState.getFluidState().isEmpty()) {
                    return false;
                }
                for (int dy = 0; dy < height; dy++) {
                    BlockPos position = feet.offset(dx, dy, dz);
                    BlockState state = evernightMinecraft.level.getBlockState(position);
                    if (!state.getFluidState().isEmpty()) {
                        return false;
                    }
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return false;
                    }
                    if (isUnstableBlock(state)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * @return whether a candidate position is above the minimum Y and surrounded by solid blocks
     */
    public static boolean isSafeSpotCandidate(int yMinusTwo, int minY, boolean solidArea) {
        return yMinusTwo >= minY && solidArea;
    }

    /**
     * Checks a 5x5x5 box around the position for anything that would make a carved pocket unstable:
     * air, fluids, lava, torches, falling blocks or any replaceable block.
     */
    private static boolean isSolidArea(BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos position = center.offset(dx, dy, dz);
                    BlockState state = evernightMinecraft.level.getBlockState(position);
                    if (state.isAir()) {
                        return false;
                    }
                    if (!state.getFluidState().isEmpty()) {
                        return false;
                    }
                    if (state.is(Blocks.LAVA) || state.is(Blocks.WATER) || state.is(Blocks.MAGMA_BLOCK)
                            || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || isUnstableBlock(state)) {
                        return false;
                    }
                    if (state.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * @return whether the block falls or is otherwise unsafe to build a storage pocket in
     */
    private static boolean isUnstableBlock(BlockState state) {
        return state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SUSPICIOUS_SAND)
                || state.is(Blocks.SUSPICIOUS_GRAVEL);
    }

    /**
     * Picks the block position to place the storage block at. Both the searched space and the
     * surrounding area are checked, and the player's own collision box is skipped so a placed block
     * never pushes the player.
     *
     * @return the position to place at, or null
     */
    public static BlockPos findPlacePosition(BlockPos center) {
        if (evernightMinecraft.player == null) {
            return null;
        }
        BlockPos playerFeet = evernightMinecraft.player.blockPosition();
        BlockPos playerHead = playerFeet.above();
        BlockPos fromCenter = findPlacePositionAround(center, playerFeet, playerHead);
        if (fromCenter != null) {
            return fromCenter;
        }
        return findPlacePositionAround(playerFeet, playerFeet, playerHead);
    }

    private static BlockPos findPlacePositionAround(BlockPos center, BlockPos playerFeet, BlockPos playerHead) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    boolean blockedByPlayer = candidate.equals(playerFeet) || candidate.equals(playerHead);
                    if (isPlaceableSlot(canPlaceAt(candidate), blockedByPlayer)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return whether the position may host a placed block without being occupied by the player
     */
    public static boolean isPlaceableSlot(boolean placeable, boolean blockedByPlayer) {
        return placeable && !blockedByPlayer;
    }

    /**
     * @return whether a block can be placed on the position: it has to be air with a non-air, non-fluid
     * block below it
     */
    public static boolean canPlaceOn(boolean targetIsAir, boolean belowIsAir, boolean belowHasNoFluid) {
        return targetIsAir && !belowIsAir && belowHasNoFluid;
    }

    public static boolean canPlaceAt(BlockPos position) {
        if (evernightMinecraft.level == null) {
            return false;
        }
        BlockState target = evernightMinecraft.level.getBlockState(position);
        BlockState below = evernightMinecraft.level.getBlockState(position.below());
        return canPlaceOn(target.isAir(), below.isAir(), below.getFluidState().isEmpty());
    }
}
