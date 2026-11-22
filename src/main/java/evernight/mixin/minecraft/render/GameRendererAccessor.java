package evernight.mixin.minecraft.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("resourcePool")
    CrossFrameResourcePool getPool();

    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform getGlobalSettings();

    @Invoker("bobView")
    void evernight$bobView(PoseStack poses, float partialTick);

    @Invoker("bobHurt")
    void evernight$bobViewHurt(PoseStack poses, float partialTick);
}