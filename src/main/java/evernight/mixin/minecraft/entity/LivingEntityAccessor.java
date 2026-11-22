package evernight.mixin.minecraft.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_EFFECT_PARTICLES")
    static EntityDataAccessor evernight$potion_swirls() {
        return null;
    }

    @Accessor("useItemRemaining")
    void setItemUseTimeLeft(int useItemRemaining);

    @Accessor("swimAmount")
    void evernight$setLeaningPitch(float swimAmount);

    @Accessor("swimAmount")
    float evernight$getLeaningPitch();

    @Accessor("swimAmountO")
    float evernight$getLastLeaningPitch();

    @Accessor("swimAmountO")
    void evernight$setLastLeaningPitch(float swimAmountO);

    @Accessor("fallFlyTicks")
    void evernight$setGlidingTicks(int fallFlyTicks);

    @Invoker("getCurrentSwingDuration")
    int getSwingDuration();
}