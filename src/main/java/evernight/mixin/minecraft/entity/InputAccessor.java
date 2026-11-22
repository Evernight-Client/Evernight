package evernight.mixin.minecraft.entity;

import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    @Mutable
    @Accessor("shift")
    void evernight$setSneak(boolean shift);

    @Mutable
    @Accessor("sprint")
    void evernight$setSprint(boolean sprint);

    @Mutable
    @Accessor("jump")
    void evernight$setJump(boolean jump);

    @Mutable
    @Accessor("forward")
    void evernight$setForward(boolean forward);

    @Mutable
    @Accessor("backward")
    void evernight$setBack(boolean backward);

    @Mutable
    @Accessor("left")
    void evernight$setLeft(boolean left);

    @Mutable
    @Accessor("right")
    void evernight$setRight(boolean right);
}