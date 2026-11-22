package evernight.client.events.input;
import evernight.client.events.core.CancellableEvent;

public final class InputPressEvent extends CancellableEvent {
    private final int code;
    private final boolean mouse;

    public InputPressEvent(int code, boolean mouse) {
        this.code = code;
        this.mouse = mouse;
    }

    public boolean isMouse() {
        return this.mouse;
    }

    public int getCode() {
        return this.code;
    }
}