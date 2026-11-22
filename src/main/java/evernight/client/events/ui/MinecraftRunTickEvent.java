package evernight.client.events.ui;
import evernight.client.events.core.CancellableEvent;

public final class MinecraftRunTickEvent extends CancellableEvent {
    private final boolean hasPendingTicks;

    public MinecraftRunTickEvent(boolean hasPendingTicks) {
        this.hasPendingTicks = hasPendingTicks;
    }

    public boolean hasPendingTicks() {
        return this.hasPendingTicks;
    }
}