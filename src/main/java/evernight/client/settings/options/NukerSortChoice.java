package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.ToDoubleFunction;

public enum NukerSortChoice implements NamedEntry {
    CLOSEST("Closest", blockPos -> {
        LocalPlayer playerForEye = ClientAccess.evernightMinecraft.player;
        Vec3 eyeSnapshot = playerForEye.getEyePosition();
        Vec3 blockCenter = blockPos.getCenter();
        return eyeSnapshot.distanceToSqr(blockCenter);
    }),
    FURTHEST("Furthest", blockPos -> {
        LocalPlayer playerForEye = ClientAccess.evernightMinecraft.player;
        Vec3 eyeSnapshot = playerForEye.getEyePosition();
        Vec3 blockCenter = blockPos.getCenter();
        return -eyeSnapshot.distanceToSqr(blockCenter);
    }),
    TOP("Top", blockPos -> -blockPos.getY()),
    BOTTOM("Bottom", blockPos -> blockPos.getY());
    private final String displayName;
    private final ToDoubleFunction<BlockPos> sortKeyFunction;

    NukerSortChoice(String displayName, ToDoubleFunction<BlockPos> sortKeyFunction) {
        this.displayName = displayName;
        this.sortKeyFunction = sortKeyFunction;
    }

    public double getSortKey(BlockPos blockPos) {
        return this.sortKeyFunction.applyAsDouble(blockPos);
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}