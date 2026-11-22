package evernight.client.modules.movement.SafeWalk;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.PlayerIsStayingOnGroundSurfaceEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.EntityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SafeWalkModule extends Module {
    private final Setting<Boolean> sneakSetting;
    private final Setting<Integer> delaySetting;
    private final ElapsedTimer edgeDelayTimer;

    public SafeWalkModule() {
        super("SafeWalk", ModuleCategory.MOVEMENT);
        BooleanSetting sneak = new BooleanSetting("Sneak", true);
        sneak.markVisibilityDependency();
        sneakSetting = add(sneak);
        NumberSetting<Integer> delay = new NumberSetting<>("Delay", 0, 0, 1000);
        delay.withUnit("ms");
        delaySetting = add(delay.dependsOn(sneakSetting));
        edgeDelayTimer = new ElapsedTimer();
    }

    @Override
    public void onDisable() {
        if (sneakSetting.getValue()) ClientAccess.evernightMinecraft.options.keyShift.setDown(false);
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        if (!sneakSetting.getValue()) return;
        BlockPos below = BlockPos.containing(ClientAccess.evernightMinecraft.player.position().add(0.0, -0.1F, 0.0));
        BlockState block = ClientAccess.evernightMinecraft.level.getBlockState(below);
        if (shouldSneakAtEdge(block)) {
            edgeDelayTimer.reset();
            ClientContext.inputOverrides.requestSneak(true);
        } else if (!edgeDelayTimer.hasElapsed((delaySetting.getValue()).longValue())) {
            ClientContext.inputOverrides.requestSneak(true);
        }
    }

    @EventHandler
    private void onGroundSurfaceQuery(PlayerIsStayingOnGroundSurfaceEvent event) {
        if (!ClientAccess.evernightMinecraft.player.isSwimming()
                && !ClientAccess.evernightMinecraft.player.isInLava()
                && !ClientAccess.evernightMinecraft.player.isInWater()
                && !sneakSetting.getValue()) event.cancel();
    }

    private boolean shouldSneakAtEdge(BlockState block) {
        return EntityUtils.hasCollisionBelow(ClientAccess.evernightMinecraft.player)
                && !ClientAccess.evernightMinecraft.player.isInWater()
                && !ClientAccess.evernightMinecraft.player.isInLava() && isUnsupportedBlock(block);
    }

    private boolean isUnsupportedBlock(BlockState block) {
        return block.is(Blocks.AIR) || block.getCollisionShape(ClientAccess.evernightMinecraft.level, BlockPos.ZERO).isEmpty();
    }
}