package evernight.client.utils.world;

import evernight.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep dark escape for the auto miner: samples the biome around the player, takes the centroid of the deep
 * dark samples and returns a spot far enough outside of it.
 */
public final class AutoMiningEscape implements ClientAccess {
    /**
     * Half size of the sampled square around the player.
     */
    private static final int SAMPLE_RADIUS = 64;
    /**
     * Grid step of the sampling.
     */
    private static final int SAMPLE_STEP = 16;
    /**
     * How far the escape target is placed away from the centroid.
     */
    private static final double ESCAPE_DISTANCE = 128.0D;
    /**
     * Horizontal distance below which the player counts as arrived.
     */
    private static final double ARRIVED_DISTANCE_SQR = 100.0D;

    private AutoMiningEscape() {
    }

    /**
     * @return whether the player is standing in the deep dark, which the miner avoids because of the
     * warden
     */
    public static boolean isDeepDark(BlockPos position) {
        if (evernightMinecraft.level == null) {
            return false;
        }
        return evernightMinecraft.level.getBiome(position).is(Biomes.DEEP_DARK);
    }

    /**
     * @return a position outside the deep dark, or null when no deep dark was sampled
     */
    public static BlockPos findEscapeSpot(BlockPos origin) {
        List<BlockPos> samples = new ArrayList<>();
        for (int offsetX = -SAMPLE_RADIUS; offsetX <= SAMPLE_RADIUS; offsetX += SAMPLE_STEP) {
            for (int offsetZ = -SAMPLE_RADIUS; offsetZ <= SAMPLE_RADIUS; offsetZ += SAMPLE_STEP) {
                BlockPos sample = origin.offset(offsetX, 0, offsetZ);
                if (isDeepDark(sample)) {
                    samples.add(sample);
                }
            }
        }
        if (samples.isEmpty()) {
            return null;
        }
        long sumX = 0L;
        long sumZ = 0L;
        for (BlockPos sample : samples) {
            sumX += sample.getX();
            sumZ += sample.getZ();
        }
        double averageX = (double) sumX / samples.size();
        double averageZ = (double) sumZ / samples.size();
        double deltaX = origin.getX() - averageX;
        double deltaZ = origin.getZ() - averageZ;
        double length = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (length < 1.0D) {
            deltaX = 1.0D;
            deltaZ = 0.0D;
            length = 1.0D;
        }
        int targetX = origin.getX() + (int) (deltaX / length * ESCAPE_DISTANCE);
        int targetZ = origin.getZ() + (int) (deltaZ / length * ESCAPE_DISTANCE);
        return new BlockPos(targetX, Math.max(origin.getY(), 0), targetZ);
    }

    /**
     * @return whether the player left the deep dark and is close to the escape target horizontally
     */
    public static boolean hasArrived(BlockPos playerPosition, BlockPos target) {
        if (target == null) {
            return false;
        }
        if (isDeepDark(playerPosition)) {
            return false;
        }
        double deltaX = playerPosition.getX() - target.getX();
        double deltaZ = playerPosition.getZ() - target.getZ();
        return deltaX * deltaX + deltaZ * deltaZ < ARRIVED_DISTANCE_SQR;
    }
}
