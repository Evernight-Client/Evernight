package evernight.client.modules.movement.Velocity;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.ClientPacketListenerHandleExplosionEvent;
import evernight.client.events.network.ClientPacketListenerHandleSetEntityMotionEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.utils.movement.VelocityModeHandler;
import net.minecraft.world.phys.Vec3;

public class PlainVelocityHandler extends VelocityModeHandler {
    public PlainVelocityHandler(VelocityModule velocityModule) {
        super(velocityModule);
    }

    @Override
    public void onExplosion(ClientPacketListenerHandleExplosionEvent event) {
        if (!this.velocityModule.explosionsSetting.getValue()) {
            return;
        }
        int horizontalPercent = this.velocityModule.horizontalSetting.getValue();
        int verticalPercent = this.velocityModule.verticalSetting.getValue();
        int sign = this.velocityModule.inverseSetting.getValue() ? -1 : 1;
        event.setVelocityX(event.getVelocityX() * (horizontalPercent * 0.01F) * sign);
        event.setVelocityY(event.getVelocityY() * (verticalPercent * 0.01F));
        event.setVelocityZ(event.getVelocityZ() * (horizontalPercent * 0.01F) * sign);
    }

    @Override
    public void onEntityMotion(ClientPacketListenerHandleSetEntityMotionEvent event) {
        if (event.getEntityId() != ClientAccess.evernightMinecraft.player.getId()) {
            return;
        }
        int horizontalPercent = this.velocityModule.horizontalSetting.getValue();
        int verticalPercent = this.velocityModule.verticalSetting.getValue();
        int sign = this.velocityModule.inverseSetting.getValue() ? -1 : 1;
        Vec3 velocity = event.getVelocity();
        if (this.shouldLeaveMotionUnchanged((float) velocity.length())) {
            return;
        }
        if (RotationManager.isMovementSyncEnabled() && velocity.x == 0.0 && velocity.z == 0.0 && Math.abs(velocity.y) < 0.0788D) {
            return;
        }
        event.setVelocity(velocity.multiply(horizontalPercent * 0.01D * sign, verticalPercent * 0.01F, horizontalPercent * 0.01D * sign));
        if (horizontalPercent == 0 && verticalPercent == 0) {
            event.cancel();
        }
    }

    private boolean shouldLeaveMotionUnchanged(float motionLength) {
        if (this.velocityModule.isLungeMotionPending() && motionLength > 0.458F) {
            this.velocityModule.setLungeMotionPending(false);
            return true;
        }
        long reductionWindow = motionLength <= 0.3D ? 200L : 100L;
        return RotationManager.isMovementSyncEnabled() && this.velocityModule.reduceSetting.getValue()
                && !ClientContext.serverStateManager.hasElapsedSinceSetback(reductionWindow);
    }
}