package evernight.client.events.input;
import evernight.client.events.core.CancellableEvent;

public final class MouseHandlerTurnPlayerEvent extends CancellableEvent {
    private final double yawDelta;
    private final double pitchDelta;

    public MouseHandlerTurnPlayerEvent(double yawDelta, double pitchDelta) {
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
    }

    public double getPitchDelta() {
        return this.pitchDelta;
    }

    public double getYawDelta() {
        return this.yawDelta;
    }
}