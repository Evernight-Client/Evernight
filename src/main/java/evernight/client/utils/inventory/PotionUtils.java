package evernight.client.utils.inventory;

import evernight.mixin.minecraft.entity.LivingEntityAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.*;

public final class PotionUtils {
    public static final Potion MISSING_POTION;
    private static final Map EFFECTS_BY_COLOR;

    static {
        EFFECTS_BY_COLOR = new HashMap();
        MISSING_POTION = new Potion("empty");
        BuiltInRegistries.MOB_EFFECT.listElements().forEach(PotionUtils::registerEffectColor);
    }

    private PotionUtils() {
    }

    public static Potion getPotion(ItemStack stack) {
        DataComponentType<PotionContents> potionComponent = DataComponents.POTION_CONTENTS;
        PotionContents contents = stack.get(potionComponent);
        if (contents == null) {
            return MISSING_POTION;
        }
        Holder potionHolder = contents.potion().orElse(null);
        return potionHolder == null ? MISSING_POTION : (Potion) potionHolder.value();
    }

    public static Set getEffectsByParticleColor(LivingEntity entity) {
        EntityDataAccessor swirlAccessor = LivingEntityAccessor.evernight$potion_swirls();
        HashSet effects = new HashSet();
        Iterator particles = ((List) entity.getEntityData().get(swirlAccessor)).iterator();
        while (particles.hasNext()) {
            if (((ParticleOptions) particles.next()) instanceof ColorParticleOption colorParticle) {
                float alpha = 1.0F;
                float red = colorParticle.getRed();
                float green = colorParticle.getGreen();
                float blue = colorParticle.getBlue();
                int color = ARGB.colorFromFloat(alpha, red, green, blue);
                Holder effect = (Holder) EFFECTS_BY_COLOR.get(color);
                if (effect != null) {
                    effects.add(effect);
                }
            }
        }
        return effects;
    }

    private static void registerEffectColor(Reference<MobEffect> reference) {
        Map colorLookup = EFFECTS_BY_COLOR;
        int effectColor = reference.value().getColor();
        int color = ARGB.color(255, effectColor);
        colorLookup.put(color, reference);
    }
}