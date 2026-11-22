package evernight.client.events.ui;

import net.minecraft.world.inventory.Slot;
import evernight.client.events.core.CancellableEvent;

public final class AbstractContainerMenuMoveItemStackToEvent extends CancellableEvent {
    private final Slot slot;

    public AbstractContainerMenuMoveItemStackToEvent(Slot slot) {
        this.slot = slot;
    }

    public Slot getSlot() {
        return this.slot;
    }
}