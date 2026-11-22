package evernight.client.modules.combat.AutoMine;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.ExtraBreakTarget;
import net.minecraft.core.BlockPos;

public final class AutoMineMiningTracker implements ClientAccess {
    private static SpeedMineModule speedMine;

    static {
        speedMine = ClientContext.getModule(SpeedMineModule.class);
    }

    private final ElapsedTimer recentTimer = new ElapsedTimer();
    private BlockPos recentPosition;

    AutoMineMiningTracker() {
    }

    public static void bindModules(ModuleRegistry registry) {
        speedMine = registry.getModule(SpeedMineModule.class);
    }

    public BlockPos getCurrentPosition() {
        ExtraBreakTarget miningData = speedMine.getExtraBreakTarget();
        if (miningData == null) {
            return null;
        }
        return miningData.getPosition();
    }

    public boolean hasMiningActivity() {
        BlockPos recent = this.getRecentPosition();
        if (recent != null && !ClientAccess.evernightMinecraft.level.isEmptyBlock(recent)) {
            return true;
        }
        return speedMine.getExtraBreakTarget() != null;
    }

    public BlockPos getRecentPosition() {
        return this.recentTimer.hasElapsed(200L) ? null : this.recentPosition;
    }

    public void rememberPosition(BlockPos position) {
        this.recentPosition = position;
        this.recentTimer.reset();
    }

    public boolean hasRecentDelayElapsed(long delayMillis) {
        return this.recentTimer.hasElapsed(delayMillis);
    }
}