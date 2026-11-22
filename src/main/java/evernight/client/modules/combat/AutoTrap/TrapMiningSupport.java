package evernight.client.modules.combat.AutoTrap;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.Setting;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class TrapMiningSupport {
    private static SpeedMineModule speedMineModule;

    static {
        speedMineModule = ClientContext.getModule(SpeedMineModule.class);
    }

    private final AutoTrapModule trapModule;
    private final List<Setting<Boolean>> bodyPartSettings;

    public TrapMiningSupport(AutoTrapModule trapModule) {
        this.trapModule = trapModule;
        Setting<Boolean> belowSetting = trapModule.belowSetting;
        Setting<Boolean> legsSetting = trapModule.legsSetting;
        Setting<Boolean> faceSetting = trapModule.faceSetting;
        Setting<Boolean> headSetting = trapModule.headSetting;
        this.bodyPartSettings = List.of(headSetting, faceSetting, legsSetting, belowSetting);
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        speedMineModule = registry.getModule(SpeedMineModule.class);
    }

    private boolean hasEnemyBelowPosition(BlockPos blockPos) {
        AABB expandedBounds = new AABB(blockPos).inflate(1.0D);
        Iterator<AbstractClientPlayer> players = ClientAccess.evernightMinecraft.level.players().iterator();
        while (players.hasNext()) {
            AbstractClientPlayer player = players.next();
            if (player != ClientAccess.evernightMinecraft.player) {
                if (!ClientContext.socialRegistry.isFriendPlayer(player)) {
                    if (!player.isDeadOrDying()) {
                        if (player.isAlive()) {
                            AABB playerBounds = player.getBoundingBox();
                            if (playerBounds.intersects(expandedBounds)) {
                                BlockPos playerPos = PlayerStateUtils.getRoundedBlockPosition(
                                        player);
                                int playerY = playerPos.getY();
                                if (playerY < blockPos.getY()) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public List collectMiningPositions() {
        SpeedMineModule speedMineSnapshot = speedMineModule;
        BlockPos miningPos = speedMineSnapshot.getMiningPos();
        if (miningPos == null) {
            speedMineSnapshot = speedMineModule;
            miningPos = speedMineSnapshot.getRebreakPos();
        }
        if (miningPos == null) {
            return Collections.emptyList();
        }
        Vec3 eyeSnapshot = ClientAccess.evernightMinecraft.player.getEyePosition();
        Vec3 blockCenter = miningPos.getCenter();
        double distance = eyeSnapshot.distanceTo(blockCenter);
        Float configuredRange = this.trapModule.rangeSetting.getValue();
        if (distance > configuredRange.floatValue() || !this.hasEnemyBelowPosition(miningPos)) {
            return Collections.emptyList();
        }
        if (this.trapModule.hasLogoutAbovePosition(miningPos)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(List.of(miningPos));
    }

    public boolean shouldUseMiningSupport() {
        if (!this.trapModule.miningSupportAllowed) {
            return false;
        }
        Iterator<Setting<Boolean>> settings = this.bodyPartSettings.iterator();
        while (settings.hasNext()) {
            Setting<Boolean> setting = settings.next();
            if (setting.getValue()) {
                return false;
            }
        }
        return true;
    }
}