package evernight.client.modules.combat.HoleFill;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.world.HoleManager;
import evernight.client.settings.core.Setting;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.Vec3Utils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.world.HoleInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SmartHoleFillPolicy extends PlainHoleFillPolicy {
    public SmartHoleFillPolicy(HoleFillModule holeFillModule) {
        super(holeFillModule);
    }

    @Override
    public boolean shouldFill(HoleInfo hole) {
        ClientLevel levelSnapshot = ClientAccess.evernightMinecraft.level;
        for (AbstractClientPlayer player : levelSnapshot.players()) {
            Setting<Boolean> selfSafetySettingRef = this.holeFillModule.selfSafetySetting;
            if (selfSafetySettingRef.getValue() && !this.isSelfSafetySatisfied(hole)) {
                continue;
            }
            Setting<Boolean> ignoreNakedsSettingRef = this.holeFillModule.ignoreNakedsSetting;
            if (ignoreNakedsSettingRef.getValue()) {
                if (!PlayerStateUtils.hasArmor(player)) {
                    continue;
                }
            }
            if (player.isDeadOrDying() || ClientAccess.evernightMinecraft.player == player) {
                continue;
            }
            if (ClientContext.socialRegistry.isFriendPlayer(player)) {
                continue;
            }
            HoleManager holeManagerSnapshot = ClientContext.holeManager;
            Vec3 playerPositionForBlockPos = player.position();
            BlockPos playerBlockPosSnapshot = BlockPos.containing(playerPositionForBlockPos);
            if (holeManagerSnapshot.intersectsHole(playerBlockPosSnapshot) || !super.shouldFill(hole)) {
                continue;
            }
            Setting<Boolean> extrapolationSettingRef = this.holeFillModule.extrapolationSetting;
            if (!extrapolationSettingRef.getValue()) {
                AABB currentBounds = BoundingBoxUtils.getInterpolationBounds(player);
                if (!this.shouldIgnorePlayerBounds(hole, player, currentBounds)) {
                    return true;
                }
                continue;
            }
            Setting<Integer> ticksSettingRef = this.holeFillModule.ticksSetting;
            for (int predictionTicks = BoundingBoxUtils.getPredictionTicks(ticksSettingRef);
                 predictionTicks > 1;
                 predictionTicks--) {
                AABB predictedBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, predictionTicks);
                if (!this.shouldIgnorePlayerBounds(hole, player, predictedBounds)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldIgnorePlayerBounds(HoleInfo hole, Player player, AABB bounds) {
        boolean playerConditionResult = true;
        if (MovementPhysics.isMoving(player)) {
            BlockPos conditionHolePos = hole.pos();
            Vec3 conditionHoleCenter = conditionHolePos.getCenter();
            playerConditionResult = MovementPhysics.isMovingToward(player, conditionHoleCenter);
        }
        Vec3 boundsCenter = BoundingBoxUtils.getBottomCenter(bounds);
        BlockPos rangeHolePos = hole.pos();
        Vec3 holeCenterSnapshot = rangeHolePos.getCenter();
        Vec3 boundsCenterSnapshot = BoundingBoxUtils.getBottomCenter(bounds);
        float smartDistance = Vec3Utils.horizontalDistance(holeCenterSnapshot, boundsCenterSnapshot);
        Setting<Float> smartRangeSettingRef = this.holeFillModule.smartRangeSetting;
        Float smartRange = smartRangeSettingRef.getValue();
        if (!(smartDistance > smartRange)) {
            double centerY = boundsCenter.y();
            BlockPos heightHolePos = hole.pos();
            if (!(centerY <= heightHolePos.getY())) {
                double boundsMinY = bounds.minY;
                BlockPos differenceHolePos = hole.pos();
                double heightDifference = boundsMinY - differenceHolePos.getY();
                return heightDifference > 4.0D || !playerConditionResult;
            }
        }
        return true;
    }

    private boolean isSelfSafetySatisfied(HoleInfo hole) {
        HoleManager holeManagerSnapshot = ClientContext.holeManager;
        BlockPos playerBlockPosSnapshot = BlockPos.containing(ClientAccess.evernightMinecraft.player.position());
        if (!holeManagerSnapshot.intersectsHole(playerBlockPosSnapshot)) {
            Vec3 playerPositionSnapshot = ClientAccess.evernightMinecraft.player.position();
            BlockPos holePos = hole.pos();
            Vec3 holeCenter = holePos.getCenter();
            double distance = playerPositionSnapshot.distanceTo(holeCenter);
            return distance >= 2.0D;
        }
        return true;
    }
}