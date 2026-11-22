package evernight.client.events.render;

import net.minecraft.client.particle.Particle;
import evernight.client.events.core.CancellableEvent;

public final class ParticleEngineAddEvent extends CancellableEvent {
    private Particle particle;

    public ParticleEngineAddEvent(Particle particle) {
        this.particle = particle;
    }

    public Particle getParticle() {
        return this.particle;
    }

    public void setParticle(Particle particle) {
        this.particle = particle;
    }
}