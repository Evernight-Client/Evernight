package evernight.client.events.network;

import net.minecraft.world.InteractionHand;
import evernight.client.events.core.CancellableEvent;

public final class MultiPlayerGameModeUseItemEvent extends CancellableEvent {
    private final InteractionHand hand;

    public MultiPlayerGameModeUseItemEvent(InteractionHand hand) {
        this.hand = hand;
    }

    public InteractionHand getHand() {
        return this.hand;
    }
}