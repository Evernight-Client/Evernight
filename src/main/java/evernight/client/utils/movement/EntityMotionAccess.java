package evernight.client.utils.movement;

import net.minecraft.world.phys.Vec3;

public interface EntityMotionAccess {
    float getCameraYaw();

    void setCameraYaw(float yaw);

    float getCameraPitch();

    void setCameraPitch(float pitch);

    Vec3 evernight$getPrevVelocity();
}