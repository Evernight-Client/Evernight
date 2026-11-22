package evernight.client.modules.combat.AnchorAura;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.utils.combat.DamageUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.network.ServerSideDeathState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;

public record AnchorTargetDamage(Player target, double damage) implements ClientAccess {
    static AnchorTargetDamage calculateForBase(AnchorAuraModule module, BlockPos position) {
        boolean ignoreMiningBlock = module.shouldIgnoreMiningBlock();
        AnchorTargetDamage bestDamage = null;
        List playerList = ClientAccess.evernightMinecraft.level.players();
        Iterator players = playerList.iterator();
        while (players.hasNext()) {
            Player candidate = (Player) players.next();
            double validationRange = (module.targetRangeSetting.getValue()).floatValue();
            if (!AnchorTargetDamage.isInvalidTarget(candidate, validationRange)) {
                Vec3 explosionPositionSnapshot = position.getCenter();
                AABB boundsSnapshot = candidate.getBoundingBox();
                BlockPos ignoredBaseSnapshot = !module.isAnchorBlock(position) && !ignoreMiningBlock ? null
                        : position;
                double damage = DamageUtils.getExplosionDamage(
                        explosionPositionSnapshot, candidate, boundsSnapshot, 5.0D, true, ignoredBaseSnapshot, null
                );
                if (!((module.minDamageSetting.getValue()).floatValue() > damage)
                        && !(damage < MathConstants.ONE_HALF)) {
                    if (bestDamage != null) {
                        if (!(damage > bestDamage.damage())) {
                            continue;
                        }
                    }
                    bestDamage = new AnchorTargetDamage(candidate, damage);
                }
            }
        }
        return bestDamage;
    }

    public static boolean isInvalidTarget(Player target, double range) {
        if (target == null || target == ClientAccess.evernightMinecraft.player || !target.isAlive()) {
            return true;
        }
        if (ClientContext.socialRegistry.isFriendPlayer(target) || ((ServerSideDeathState) target).isServerSideDead()) {
            return true;
        }
        if (target.isSpectator() || target.getAbilities().instabuild) {
            return true;
        }
        return ClientAccess.evernightMinecraft.player.distanceTo(target) > range;
    }
}