package evernight.mixin.minecraft.entity;

import net.minecraft.world.entity.PositionMoveRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PositionMoveRotation.class)
public interface PositionMoveRotationAccessor {
    @Mutable
    @Accessor("yRot")
    void evernight$setYaw(float yRot);

    @Mutable
    @Accessor("xRot")
    void evernight$setPitch(float xRot);
}