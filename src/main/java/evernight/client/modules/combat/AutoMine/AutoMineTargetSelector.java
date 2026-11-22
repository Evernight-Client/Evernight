package evernight.client.modules.combat.AutoMine;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.RotationManager;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

public final class AutoMineTargetSelector implements ClientAccess {
    private final AutoMineModule module;

    AutoMineTargetSelector(AutoMineModule module) {
        this.module = module;
    }

    public AABB getTargetFootprintBounds(Player target) {
        AABB boundsSnapshot = target.getBoundingBox();
        double shrink = -BoundingBoxUtils.blockCornerInset;
        boundsSnapshot = boundsSnapshot.inflate(shrink, 0.0, shrink);
        double maximumY = target.getY();
        return boundsSnapshot.setMaxY(maximumY);
    }

    public Player selectTarget() {
        Stream<AbstractClientPlayer> filteredCandidates = ClientAccess.evernightMinecraft.level.players().stream()
                .filter(candidate -> candidate.isAlive() && candidate != ClientAccess.evernightMinecraft.player
                        && ClientAccess.evernightMinecraft.player.distanceTo(candidate) <= this.module.getRange() + 2.0F
                        && !ClientContext.socialRegistry.isFriendPlayer(candidate))
                .filter(candidate -> !this.module.ignoreNakedSetting.getValue() || PlayerStateUtils.hasArmor(candidate));
        Comparator<AbstractClientPlayer> yawOrder = Comparator.comparing(candidate -> {
            float yawSnapshot = ClientAccess.evernightMinecraft.player.getYRot();
            float candidateYaw = RotationManager.getRotationsToEntity(candidate)[0];
            float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw);
            return yawDifference;
        });
        return filteredCandidates.min(yawOrder).orElse(null);
    }
}