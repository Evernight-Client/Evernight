package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.movement.LongJump.LongJumpModule;
import evernight.client.modules.movement.Speed.SpeedModeHandler;
import evernight.client.modules.movement.Speed.SpeedModule;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.MathConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class StrictStrafeSpeedHandler extends SpeedModeHandler {
    private static LongJumpModule longJumpModule;

    public StrictStrafeSpeedHandler(SpeedModule speedModule) {
        super(speedModule);
    }

    public static void bindModules(ModuleRegistry modules) {
        longJumpModule = modules.getModule(LongJumpModule.class);
    }

    @Override
    public void onRotationUpdate() {
        if (this.shouldPauseStrictStrafe() || !MovementPhysics.isLocalPlayerMoving()
                || !ClientAccess.evernightMinecraft.player.verticalCollisionBelow) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        double[] motion = MovementPhysics.getHorizontalMotion(player.getYRot(), player.input, 1.0D);
        float movementYaw = (float) (Math.toDegrees(Math.atan2(motion[1], motion[0])) - MathConstants.NINETY_DEGREES);
        float radians = (float) Math.toRadians(movementYaw);
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, 0.4D + MovementPhysics.getJumpBoost(), velocity.z);
        player.addDeltaMovement(new Vec3(-Mth.sin(radians) * 0.2F, 0.0, Mth.cos(radians) * 0.2F));
        player.setSprinting(true);
    }

    @Override
    public void onMove(LocalPlayerMoveEvent event) {
        if (this.shouldPauseStrictStrafe()) {
            return;
        }
        if (this.speedModule.useTimerSetting.getValue()
                && ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
            ClientContext.tickRateController.requestMultiplier(this.speedModule, 1.0888F);
        } else {
            ClientContext.tickRateController.releaseMultiplier(this.speedModule);
        }
        if (!MovementPhysics.isLocalPlayerMoving()) {
            event.setHorizontalVelocity(0.0, 0.0);
            return;
        }
        double speed = MovementPhysics.getBaseMoveSpeed(false);
        if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
            speed = Math.max(speed, Math.hypot(event.getX(), event.getZ()));
        }
        MovementPhysics.applyHorizontalMotion(event, speed);
    }

    private boolean shouldPauseStrictStrafe() {
        return this.speedModule.shouldPauseInBlocks() || this.speedModule.shouldPauseInLiquid()
                || PlayerStateUtils.isHungerLimited() || ClientAccess.evernightMinecraft.player.isFallFlying()
                || ClientAccess.evernightMinecraft.player.isSpectator() || longJumpModule.isToggled();
    }
}