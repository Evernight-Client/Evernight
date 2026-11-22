package evernight.client.events.player;

import net.minecraft.world.effect.MobEffectInstance;
import evernight.client.events.core.CancellableEvent;

public final class LivingEntityAddEffectEvent extends CancellableEvent {
    private final MobEffectInstance effect;

    public LivingEntityAddEffectEvent(MobEffectInstance effect) {
        this.effect = effect;
    }

    public MobEffectInstance getEffect() {
        return this.effect;
    }
}