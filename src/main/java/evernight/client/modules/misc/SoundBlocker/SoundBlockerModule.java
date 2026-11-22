package evernight.client.modules.misc.SoundBlocker;

import evernight.client.events.core.EventHandler;
import evernight.client.events.render.SoundEnginePlayEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.Set;

public class SoundBlockerModule extends Module {
    private final Setting<Set<SoundEvent>> soundsSetting;

    public SoundBlockerModule() {
        super("SoundBlocker", ModuleCategory.MISC);
        this.soundsSetting = this.add(new RegistrySetSetting<>("Sounds", BuiltInRegistries.SOUND_EVENT));
    }

    @EventHandler
    private void onSoundEnginePlayEvent(SoundEnginePlayEvent event) {
        for (SoundEvent sound : this.soundsSetting.getValue()) {
            Identifier blockedIdentifier = sound.location();
            SoundInstance playingSound = event.getSoundInstance();
            if (blockedIdentifier.equals(playingSound.getIdentifier())) {
                event.cancel();
                break;
            }
        }
    }
}