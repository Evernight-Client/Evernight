package evernight.client.modules.movement.FastStairs;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;

public class FastStairsModule extends Module {
    private static final MutableBlockPos scanPosition;

    static {
        scanPosition = new MutableBlockPos();
    }

    private final ElapsedTimer jumpTimer = new ElapsedTimer();
    private boolean roseSincePreviousTick;
    private float capturedSpeed;

    public FastStairsModule() {
        super("FastStairs", ModuleCategory.MOVEMENT);
    }

    private boolean hasNearbyStairs(BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z < 2; z++) {
                scanPosition.set(center.getX() + x, center.getY(), center.getZ() + z);
                if (WorldUtils.getBlock(scanPosition) instanceof StairBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.capturedSpeed = (float) ClientContext.movementSpeedTracker.getCurrentSpeed();
        double previousY = ClientAccess.evernightMinecraft.player.yo;
        this.roseSincePreviousTick = previousY < ClientAccess.evernightMinecraft.player.getY();
    }

    @EventHandler
    public void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        AABB feetBounds = player.getBoundingBox().move(0.0, -0.1D, 0.0);
        if (BlockPos.betweenClosedStream(feetBounds).noneMatch(pos -> WorldUtils.getBlock(pos) instanceof StairBlock)) {
            return;
        }
        BlockPos playerPosition = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        if (!player.onGround() || !this.roseSincePreviousTick || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!this.hasNearbyStairs(playerPosition.above()) || this.capturedSpeed < 20.0F) {
            return;
        }
        if (!this.jumpTimer.hasElapsedAndReset(150L)) {
            return;
        }
        ClientContext.inputOverrides.requestJump(true);
    }
}