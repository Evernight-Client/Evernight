package evernight.client.modules.player.NoEffects;

import evernight.client.events.core.EventHandler;
import evernight.client.events.player.StatusEffectQueryEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Set;

public class NoEffectsModule extends Module {
    private final Setting<Set<MobEffect>> effectsSetting;

    public NoEffectsModule() {
        super("NoEffects", ModuleCategory.PLAYER);
        effectsSetting = add(new RegistrySetSetting<>("Effects", BuiltInRegistries.MOB_EFFECT));
        List<MobEffect> defaults = List.of(MobEffects.LEVITATION.value(), MobEffects.SLOW_FALLING.value());
        selectedEffects().addAll(defaults);
        defaultEffects().addAll(defaults);
    }

    public boolean blocksEffect(Holder<MobEffect> effect) {
        return selectedEffects().contains(effect.value());
    }

    @EventHandler
    private void onEffectQuery(StatusEffectQueryEvent event) {
        if (blocksEffect(event.getEffect())) {
            event.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    private Set<MobEffect> selectedEffects() {
        return effectsSetting.getValue();
    }

    @SuppressWarnings("unchecked")
    private Set<MobEffect> defaultEffects() {
        return effectsSetting.getDefaultValue();
    }
}