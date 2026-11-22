package evernight.client.events.player;
import evernight.client.events.core.CancellableEvent;

public class SelectedSlotChangeEvent extends CancellableEvent {
    private final int slot;

    public SelectedSlotChangeEvent(int slot) {
        this.slot = slot;
    }

    public int getSlot() {
        return this.slot;
    }

    public static final class ClientPacketListenerHandleSetHeldSlotEvent extends CancellableEvent {
        private final int slot;

        public ClientPacketListenerHandleSetHeldSlotEvent(int slot) {
            this.slot = slot;
        }

        public int getSlot() {
            return this.slot;
        }
    }
}