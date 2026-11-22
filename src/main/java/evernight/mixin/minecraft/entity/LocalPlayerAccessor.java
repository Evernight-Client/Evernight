package evernight.mixin.minecraft.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("lastOnGround")
    boolean lastOnGround();

    @Accessor("yRotLast")
    float lastYaw();

    @Invoker("modifyInput")
    Vec2 evernight$applyMovementSpeedFactors(Vec2 input);

    @Accessor("positionReminder")
    void evernight$setTicksSinceLastPositionPacketSent(int positionReminder);

    @Accessor("positionReminder")
    int evernight$getTicksSinceLastPositionPacketSent();

    @Accessor("lastSentInput")
    void evernight$setLastPlayerInput(Input lastSentInput);

    @Accessor("wasSprinting")
    void evernight$setLastSprinting(boolean wasSprinting);
}