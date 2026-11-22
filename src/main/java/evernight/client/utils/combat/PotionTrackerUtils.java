package evernight.client.utils.combat;

import evernight.client.utils.inventory.PotionUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Potion-effect formatting for the NameTags module.
 *
 * <p>Each effect becomes one compact {@code name amplifier duration} line so the nametag can draw
 * them below the name, either on separate lines or joined into a single one.
 *
 * <p>Vanilla only sends effect updates to the affected player, so the synced effect list of every
 * other player is empty on the client. Their effects are reconstructed from the synced potion swirl
 * particles instead, which carries neither amplifier nor duration information.
 */
public final class PotionTrackerUtils {
    private PotionTrackerUtils() {
        throw new AssertionError();
    }

    /**
     * Formats every visible effect, sorted by name. Harmful effects are only included while
     * {@code showNegative} is on. Uses the synced effect instances when available (the local player)
     * and falls back to particle colours for remote players.
     *
     * @param splitLines when {@code false} all effects are joined into a single line
     */
    public static List<String> formatLines(
            Player player, boolean showNegative, boolean showDuration, boolean splitLines) {
        List<String> lines = collectEffectLines(player, showNegative, showDuration);
        if (lines.isEmpty()) {
            lines = collectParticleLines(player, showNegative);
        }
        if (splitLines || lines.isEmpty()) {
            return lines;
        }
        return List.of(String.join(", ", lines));
    }

    private static List<String> collectEffectLines(Player player, boolean showNegative, boolean showDuration) {
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (!showNegative && !instance.getEffect().value().isBeneficial()) {
                continue;
            }
            effects.add(instance);
        }
        effects.sort(Comparator.comparing(instance -> instance.getEffect().value().getDisplayName().getString()));
        List<String> lines = new ArrayList<>(effects.size());
        for (MobEffectInstance instance : effects) {
            StringBuilder builder = new StringBuilder();
            builder.append(instance.getEffect().value().getDisplayName().getString());
            if (instance.getAmplifier() > 0) {
                builder.append(' ').append(instance.getAmplifier() + 1);
            }
            if (showDuration) {
                builder.append(' ').append(instance.getDuration() / 20).append('s');
            }
            lines.add(builder.toString());
        }
        return lines;
    }

    private static List<String> collectParticleLines(Player player, boolean showNegative) {
        Set<?> particleEffects = PotionUtils.getEffectsByParticleColor(player);
        if (particleEffects.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(particleEffects.size());
        for (Object particleEffect : particleEffects) {
            if (!(particleEffect instanceof Holder<?> holder) || !(holder.value() instanceof MobEffect effect)) {
                continue;
            }
            if (!showNegative && !effect.isBeneficial()) {
                continue;
            }
            lines.add(effect.getDisplayName().getString());
        }
        lines.sort(String::compareTo);
        return lines;
    }
}
