package evernight.mixin.minecraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack")
    boolean attack();

    @Invoker("startUseItem")
    void interact();

    @Accessor("rightClickDelay")
    int getItemUseCooldown();

    @Accessor("rightClickDelay")
    void setItemUseCooldown(int rightClickDelay);

    @Accessor("clientLevelTeardownInProgress")
    boolean evernight$isDisconnecting();

    @Mutable
    @Accessor("mainRenderTarget")
    void evernight$setFramebuffer(RenderTarget mainRenderTarget);

    @Accessor("mainRenderTarget")
    RenderTarget evernight$getFrambuffer();
}