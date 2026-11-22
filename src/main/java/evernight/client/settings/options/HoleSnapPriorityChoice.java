package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.managers.player.RotationManager;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.math.Vec3Utils;
import evernight.client.utils.world.HoleInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public enum HoleSnapPriorityChoice implements ClientAccess, NamedEntry {
    DISTANCE("Distance", Comparator.comparing((HoleInfo hole) -> {
        BlockPos block = hole.pos();
        Vec3 center = block.getCenter();
        LocalPlayer capturedPlayer = ClientAccess.evernightMinecraft.player;
        Vec3 playerPosition = capturedPlayer.position();
        return Vec3Utils.horizontalDistance(center, playerPosition);
    })),
    CROSSHAIR("Crosshair", Comparator.comparing((HoleInfo hole) -> {
        GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        float cameraYaw = camera.yaw();
        BlockPos block = hole.pos();
        Vec3 center = block.getCenter();
        float targetYaw = RotationManager.getRotationsToPosition(center)[0];
        float yawDifference = Mth.degreesDifferenceAbs(cameraYaw, targetYaw);
        return yawDifference;
    }));
    private final String displayName;
    private final Comparator<HoleInfo> comparator;

    HoleSnapPriorityChoice(String displayName, Comparator<HoleInfo> comparator) {
        this.displayName = displayName;
        this.comparator = comparator;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public Comparator<HoleInfo> getComparator() {
        return this.comparator;
    }
}