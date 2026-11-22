package evernight.client.modules.movement.Speed;

import evernight.client.ClientAccess;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.utils.movement.MovementPhysics;

public final class VanillaSpeedHandler extends SpeedModeHandler {
    public VanillaSpeedHandler(SpeedModule speedModule) {
        super(speedModule);
    }

    @Override
    public void onMove(LocalPlayerMoveEvent event) {
        if (this.speedModule.shouldPauseInBlocks() || this.speedModule.shouldPauseInLiquid()
                || ClientAccess.evernightMinecraft.player.isFallFlying() || this.speedModule.shouldYieldToObstaclePasser()) {
            return;
        }
        double speed = this.speedModule.speedSetting.getValue();
        MovementPhysics.applyHorizontalMotion(event, MovementPhysics.applySpeedEffects(false, speed));
    }
}