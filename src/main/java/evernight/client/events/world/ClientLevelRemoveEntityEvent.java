package evernight.client.events.world;
import evernight.client.events.core.CancellableEvent;

public final class ClientLevelRemoveEntityEvent extends CancellableEvent {
    private final int id;
    private final boolean entityPresent;

    public ClientLevelRemoveEntityEvent(int id, boolean entityPresent) {
        this.id = id;
        this.entityPresent = entityPresent;
    }

    public boolean isEntityPresent() {
        return this.entityPresent;
    }

    public int getId() {
        return this.id;
    }
}