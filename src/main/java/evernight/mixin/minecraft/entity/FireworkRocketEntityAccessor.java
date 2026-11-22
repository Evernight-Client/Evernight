package evernight.mixin.minecraft.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    @Accessor("life")
    int getLife();

    @Accessor("life")
    void setLife(int life);

    @Accessor("lifetime")
    int getLifeTime();

    @Accessor("attachedToEntity")
    LivingEntity evernight$getShooter();
}