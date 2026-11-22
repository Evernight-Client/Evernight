package evernight.client.modules.movement.Speed;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.utils.movement.MovementPhysics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;

public abstract class SpeedModeHandler implements ClientAccess {
    protected final SpeedModule speedModule;
    protected double moveSpeed;
    protected double lastHorizontalDistance;
    protected int movementStage;

    protected SpeedModeHandler(SpeedModule speedModule) {
        this.speedModule = speedModule;
    }

    public void onRotationUpdate() {
    }

    public boolean hasVerticalMotionCollision() {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        double verticalMotion = ClientAccess.evernightMinecraft.player.getDeltaMovement().y;
        return !level.noCollision(bounds.move(0.0, verticalMotion, 0.0));
    }

    public void onEnable() {
    }

    public void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (EventStage.Post == event.getStage()) {
            return;
        }
        if (!MovementPhysics.isLocalPlayerMoving()) {
            this.movementStage = 4;
            this.moveSpeed = 0.0;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        double deltaX = player.getX() - player.xo;
        double deltaZ = player.getZ() - player.zo;
        this.lastHorizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    public abstract void onMove(LocalPlayerMoveEvent event);
}