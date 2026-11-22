package evernight.client.events.player;

import net.minecraft.core.Holder;
import evernight.client.events.core.CancellableEvent;

public final class StatusEffectQueryEvent extends CancellableEvent {
    private final Holder effect;

    public StatusEffectQueryEvent(Holder effect) {
        this.effect = effect;
    }

    public Holder getEffect() {
        return this.effect;
    }
}