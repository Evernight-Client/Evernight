package evernight.client.audio;

import evernight.client.ClientAccess;
import evernight.mixin.minecraft.client.AbstractSoundInstanceAccessor;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayInputStream;

public class SoundClip implements ClientAccess {
    public static final SoundClip NOOP = new SoundClip(null);
    private final byte[] soundBytes;

    public SoundClip(byte[] soundBytes) {
        this.soundBytes = soundBytes;
    }

    public void play() {
        play(1.0F);
    }

    public void playAt(Vec3 position) {
        playAt(position, 1.0F);
    }

    public void play(float volume) {
        playAt(null, volume);
    }

    public void playAt(Vec3 position, float volume) {
        if (this.soundBytes == null) return;
        var minecraft = ClientAccess.evernightMinecraft;
        minecraft.execute(() -> {
            if (minecraft.getSoundManager() == null || position != null && minecraft.level == null) return;
            try {
                var stream = new ByteArrayInputStream(this.soundBytes);
                StreamSoundInstance sound = position == null ? new StreamSoundInstance(stream) : new StreamSoundInstance(position, stream);
                ((AbstractSoundInstanceAccessor) sound).setVolume(volume);
                minecraft.getSoundManager().play(sound);
            } catch (Exception failure) {
                ClientAccess.logger.warn("Could not play sound", failure);
            }
        });
    }
}