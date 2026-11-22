package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.managers.player.RotationManager;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public enum ChorusControlYawChoice implements ClientAccess, NamedEntry {
    KEEP("Keep"),
    INVERT("Invert"),
    POS("Pos");
    private final String displayName;

    ChorusControlYawChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public float resolveYaw(Vec3 destination, Vec3 eyePosition) {
        if (this == KEEP) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            return player.getYRot();
        }
        float yaw = RotationManager.calculateRotations(destination, eyePosition)[0];
        if (this == INVERT) {
            yaw += 180.0F;
        }
        return yaw;
    }
}