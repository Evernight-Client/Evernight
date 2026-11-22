package evernight.client.modules.movement.ElytraFly;

import evernight.client.ClientAccess;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.movement.ElytraFlyModeHandler;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import net.minecraft.core.Direction.Axis;

import static evernight.client.utils.math.MathConstants.FULL_TURN_DEGREES;
import evernight.client.utils.math.MathConstants;

public class PacketElytraFlyHandler extends ElytraFlyModeHandler {
    private final ElapsedTimer idleMovementTimer = new ElapsedTimer();
    private long accelerationStartTimeMillis;
    private float flightSpeed;

    public PacketElytraFlyHandler(ElytraFlyModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        this.flightSpeed = 0.0F;
        this.accelerationStartTimeMillis = System.currentTimeMillis();
    }

    @Override
    public void handleMovement(LocalPlayerMoveEvent event) {
        if (!this.module.isPacketFlightActive()) {
            return;
        }
        if (!MovementPhysics.isLocalPlayerMoving()) {
            this.accelerationStartTimeMillis = System.currentTimeMillis();
        }
        if (this.module.accelerateSetting.getValue()) {
            this.flightSpeed = (float) MovementPhysics.accelerateTo(
                    this.module.flightSpeedSetting.getValue(), this.module.accelMinSetting.getValue(),
                    this.module.accelTimeSetting.getValue(), this.accelerationStartTimeMillis);
        } else {
            this.flightSpeed = this.module.flightSpeedSetting.getValue();
        }
        double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, this.flightSpeed);
        float glideMotion = -this.module.glideSetting.getValue();
        ClientAccess.evernightMinecraft.player.setDeltaMovement(horizontalMotion[0], glideMotion, horizontalMotion[1]);
        event.setY(glideMotion);
        if (ClientAccess.evernightMinecraft.player.input.keyPresses.shift()) {
            event.setY(-0.5D);
            ClientAccess.evernightMinecraft.player.setDeltaMovement(
                    ClientAccess.evernightMinecraft.player.getDeltaMovement().with(Axis.Y, -0.5D));
        }
        PacketSender.sendStartFallFlying();
        if (this.module.antiAfkSetting.getValue() && event.getX() == 0.0 && event.getZ() == 0.0
                && this.idleMovementTimer.hasElapsed(2500L)) {
            double radians = Math.toRadians(ClientAccess.evernightMinecraft.player.tickCount % FULL_TURN_DEGREES);
            event.setHorizontalVelocity(Math.sin(radians) * 0.03D, Math.cos(radians) * 0.03D);
            return;
        }
        if (MovementPhysics.isLocalPlayerMoving()) {
            this.idleMovementTimer.reset();
        }
    }
}