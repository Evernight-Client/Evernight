package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.integrations.ProtocolCompatibility;
import evernight.client.integrations.ProtocolVersionAccess;
import evernight.mixin.minecraft.entity.LocalPlayerAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class MovementInputProcessor implements ClientAccess {
    public void applyInputPreservingBobbing() {
        float previousYawBob = ClientAccess.evernightMinecraft.player.yBobO;
        float previousPitchBob = ClientAccess.evernightMinecraft.player.xBobO;
        float pitchBob = ClientAccess.evernightMinecraft.player.xBob;
        float yawBob = ClientAccess.evernightMinecraft.player.yBob;
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        player.applyInput();
        ClientAccess.evernightMinecraft.player.yBobO = previousYawBob;
        ClientAccess.evernightMinecraft.player.xBobO = previousPitchBob;
        ClientAccess.evernightMinecraft.player.xBob = pitchBob;
        ClientAccess.evernightMinecraft.player.yBob = yawBob;
    }

    public Vec2 normalizeInput(Vec2 input) {
        ProtocolVersionAccess targetVersion = ProtocolCompatibility.getTargetVersion();
        if (targetVersion.olderEq_1_21_4()) {
            return ((LocalPlayerAccessor) ClientAccess.evernightMinecraft.player)
                    .evernight$applyMovementSpeedFactors(input);
        }
        return input.normalized();
    }

    public MovementDirection getRelativeDirection(float movementYaw, float rotationYaw) {
        double difference = (Mth.degreesDifference(movementYaw, rotationYaw) + 360.0D) % 360.0D;
        MovementDirection[] directions = MovementDirection.values();
        return directions[(int) Math.round(difference / 45.0D) % directions.length];
    }
}