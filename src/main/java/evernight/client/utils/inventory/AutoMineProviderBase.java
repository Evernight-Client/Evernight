package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.combat.AutoMine.AutoMineMiningTracker;
import evernight.client.modules.combat.AutoMine.AutoMineModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.BlockSampleMode;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public abstract class AutoMineProviderBase implements ClientAccess, AutoMineProvider {
    private static SpeedMineModule speedMine;

    static {
        speedMine = ClientContext.getModule(SpeedMineModule.class);
    }

    protected final AutoMineModule autoMine;

    public AutoMineProviderBase(AutoMineModule module) {
        this.autoMine = module;
    }

    public static void bindModules(ModuleRegistry registry) {
        speedMine = registry.getModule(SpeedMineModule.class);
    }

    protected boolean hasNoCollisionBelow(Entity entity) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        AABB collisionBounds = entity.getBoundingBox().expandTowards(0.0, -0.1D, 0.0);
        return level.noBlockCollision(entity, collisionBounds);
    }

    protected boolean canMinePosition(BlockPos position) {
        AutoMineMiningTracker miningTracker = this.autoMine.miningTracker;
        if (position.equals(miningTracker.getRecentPosition())) {
            return false;
        }
        if (!this.canStartMiningPosition(position)) {
            return false;
        }
        Setting<Boolean> strictDirectionSetting = this.autoMine.strictDirectionSetting;
        if (strictDirectionSetting.getValue()) {
            if (BlockInteractionUtils.getStrictDirections(position).isEmpty()) {
                return false;
            }
        }
        Setting<Boolean> raytraceSetting = this.autoMine.raytraceSetting;
        if (raytraceSetting.getValue()) {
            List<Vec3> raytracePoints = BlockSampleMode.X8.sampleBlock(position);
            if (!WorldUtils.canSeeAny(raytracePoints)) {
                return false;
            }
        }
        if (this.autoMine.hasAssistantBreakProgress(position)) {
            return false;
        }
        return WorldUtils.canMineBlock(position);
    }

    protected boolean canStartMiningPosition(BlockPos position) {
        return speedMine.isInMiningRange(position) && speedMine.isBlockAllowed(position);
    }

    protected boolean isAlreadyMining(BlockPos position) {
        return position != null && (position.equals(speedMine.getMiningPos()) || position.equals(speedMine.getRebreakPos()));
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}