package evernight.client.events.player;
import evernight.client.events.core.CancellableEvent;

public final class SneakStateChangeEvent extends CancellableEvent {
    private final boolean sneaking;

    public SneakStateChangeEvent(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSneaking() {
        return this.sneaking;
    }
}