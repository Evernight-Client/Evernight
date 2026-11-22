package evernight.client.events.world;
import evernight.client.events.core.CancellableEvent;

public final class FireworkRocketEntityTickEvent extends CancellableEvent {
    private float yaw;
    private float pitch;

    public FireworkRocketEntityTickEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
        this.cancel();
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
        this.cancel();
    }
}