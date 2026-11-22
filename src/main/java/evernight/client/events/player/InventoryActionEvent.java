package evernight.client.events.player;
import evernight.client.events.core.CancellableEvent;
import evernight.client.events.core.EventStage;

public final class InventoryActionEvent extends CancellableEvent {
    private final EventStage stage;

    public InventoryActionEvent(EventStage stage) {
        this.stage = stage;
    }

    public EventStage getStage() {
        return this.stage;
    }
}