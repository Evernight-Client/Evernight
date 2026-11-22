package evernight.client.modules.movement.ObstaclePasser;

import evernight.client.ClientAccess;
import evernight.client.managers.player.RotationManager;
import net.minecraft.core.BlockPos;

public enum ObstacleAlignmentDirection implements ClientAccess {
    XP, XN, ZP, ZN, XP_ZP, XN_ZP, XP_ZN, XN_ZN;

    public static ObstacleAlignmentDirection fromYaw(float yaw) {
        return switch (RotationManager.getYawSector(yaw)) {
            case 0 -> ZP;
            case 1 -> XN_ZP;
            case 2 -> XN;
            case 3 -> XN_ZN;
            case 4 -> ZN;
            case 5 -> XP_ZN;
            case 6 -> XP;
            case 7 -> XP_ZP;
            default -> null;
        };
    }

    public BlockPos positionAtOffset(Integer offset) {
        // The axis perpendicular to the travel direction must stay at the player's position:
        // Baritone's GoalBlock is an absolute coordinate, so replacing it with 0 would walk to Z=0/X=0.
        double x = ClientAccess.evernightMinecraft.player.getX();
        double z = ClientAccess.evernightMinecraft.player.getZ();
        int step = offset;
        return switch (this) {
            case XP -> BlockPos.containing(x + step, 0.0, z);
            case XN -> BlockPos.containing(x - step, 0.0, z);
            case ZP -> BlockPos.containing(x, 0.0, z + step);
            case ZN -> BlockPos.containing(x, 0.0, z - step);
            case XP_ZP -> BlockPos.containing(x + step, 0.0, z + step);
            case XN_ZP -> BlockPos.containing(x - step, 0.0, z + step);
            case XP_ZN -> BlockPos.containing(x + step, 0.0, z - step);
            case XN_ZN -> BlockPos.containing(x - step, 0.0, z - step);
        };
    }
}