package evernight.client.events.player;
import evernight.client.events.core.CancellableEvent;

public final class LivingEntityRotationEvent extends CancellableEvent {
    private final boolean jump;
    private float yaw;
    private float pitch;

    public LivingEntityRotationEvent(float yaw, float pitch, boolean jump) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.jump = jump;
    }

    public boolean isJump() {
        return this.jump;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public static final class LivingEntityAiStepEvent extends CancellableEvent {
        private final float yaw;
        private final float pitch;

        public LivingEntityAiStepEvent(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public float getPitch() {
            return this.pitch;
        }

        public float getYaw() {
            return this.yaw;
        }
    }
}