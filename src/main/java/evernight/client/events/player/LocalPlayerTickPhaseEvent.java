package evernight.client.events.player;
import evernight.client.events.core.CancellableEvent;

public abstract class LocalPlayerTickPhaseEvent extends CancellableEvent {
    public static final class LocalPlayerTickEvent extends LocalPlayerTickPhaseEvent {
    }

    public static final class BeforePassengerCheck extends LocalPlayerTickPhaseEvent {
    }
}