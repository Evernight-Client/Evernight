package evernight.client.events.render;

import net.minecraft.client.resources.sounds.SoundInstance;
import evernight.client.events.core.CancellableEvent;

public final class SoundEnginePlayEvent extends CancellableEvent {
    private final SoundInstance soundInstance;

    public SoundEnginePlayEvent(SoundInstance soundInstance) {
        this.soundInstance = soundInstance;
    }

    public SoundInstance getSoundInstance() {
        return this.soundInstance;
    }
}