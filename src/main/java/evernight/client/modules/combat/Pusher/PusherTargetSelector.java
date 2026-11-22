package evernight.client.modules.combat.Pusher;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.world.WorldStateManager;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PusherTargetSelector implements ClientAccess {
    private static final List<Block> excludedHeadBlocks;

    static {
        Block respawnAnchor = Blocks.RESPAWN_ANCHOR;
        Block reinforcedDeepslate = Blocks.REINFORCED_DEEPSLATE;
        Block cryingObsidian = Blocks.CRYING_OBSIDIAN;
        Block obsidian = Blocks.OBSIDIAN;
        excludedHeadBlocks = List.of(obsidian, cryingObsidian, reinforcedDeepslate, respawnAnchor);
    }

    private final PusherModule module;

    PusherTargetSelector(PusherModule module) {
        this.module = module;
    }

    private boolean isValidTarget(Player target) {
        BlockPos position = target.blockPosition();
        ClientLevel headLevelSnapshot = ClientAccess.evernightMinecraft.level;
        BlockPos headPosition = position.above();
        BlockState headState = headLevelSnapshot.getBlockState(headPosition);
        if (!excludedHeadBlocks.contains(headState.getBlock())) {
            float headResistance = headState.getDestroySpeed(ClientAccess.evernightMinecraft.level, position.above());
            if (headResistance != -1.0F) {
                WorldStateManager blockTrackerSnapshot = ClientContext.worldStateManager;
                BlockPos clearanceSnapshot = position.above(2);
                if (!blockTrackerSnapshot.isPlacementOccupied(clearanceSnapshot)) {
                    if (this.module.onlyHoleSetting.getValue()) {
                        if (!ClientContext.holeManager.intersectsHole(position)) {
                            return false;
                        }
                    }
                    if (this.module.onlySafeSetting.getValue()) {
                        if (!ClientContext.holeManager.intersectsHole(position)) {
                            headLevelSnapshot = ClientAccess.evernightMinecraft.level;
                            BlockState safeState = headLevelSnapshot.getBlockState(target.blockPosition());
                            Block safeBlock = safeState.getBlock();
                            float safeResistance = safeBlock.getExplosionResistance();
                            if (safeResistance < 600.0F) {
                                return false;
                            }
                        }
                    }
                    int occupiedCount = PlayerStateUtils.getSurroundPositions(target).size();
                    return occupiedCount <= 6;
                }
            }
        }
        return false;
    }

    public Player selectTarget() {
        Optional<AbstractClientPlayer> selectedSnapshot = ClientAccess.evernightMinecraft.level.players().stream().filter(candidate -> {
            if (candidate.isAlive() && candidate != ClientAccess.evernightMinecraft.player) {
                AABB bounds = candidate.getBoundingBox();
                double height = bounds.getYsize();
                if (height > 1.0D) {
                    if (EntityUtils.hasCollisionBelow(candidate)) {
                        float distance = ClientAccess.evernightMinecraft.player.distanceTo(candidate);
                        PusherModule moduleSnapshot = this.module;
                        if (distance <= moduleSnapshot.getRange()) {
                            return !ClientContext.socialRegistry.isFriendPlayer(candidate);
                        }
                    }
                }
            }
            return false;
        }).filter(candidate -> {
            if (this.module.ignoreNakedSetting.getValue()) {
                return PlayerStateUtils.hasArmor(candidate);
            }
            return true;
        }).filter(this::isValidTarget).min(Comparator.comparing(candidate -> {
            float yawSnapshot = ClientAccess.evernightMinecraft.player.getYRot();
            float candidateYaw = RotationManager.getRotationsToEntity(candidate)[0];
            float yawDifference = Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw);
            return yawDifference;
        }));
        return selectedSnapshot.orElse(null);
    }
}