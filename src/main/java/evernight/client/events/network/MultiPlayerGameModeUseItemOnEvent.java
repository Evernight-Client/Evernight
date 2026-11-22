package evernight.client.events.network;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import evernight.client.events.core.CancellableEvent;

public final class MultiPlayerGameModeUseItemOnEvent extends CancellableEvent {
    private final BlockHitResult hitResult;
    private final InteractionHand hand;

    public MultiPlayerGameModeUseItemOnEvent(BlockHitResult hitResult, InteractionHand hand) {
        this.hitResult = hitResult;
        this.hand = hand;
    }

    public InteractionHand getHand() {
        return this.hand;
    }

    public BlockHitResult getHitResult() {
        return this.hitResult;
    }
}