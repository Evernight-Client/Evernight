package evernight.mixin.minecraft.render;

import evernight.client.ClientContext;
import evernight.client.modules.player.Freecam.FreecamModule;
import evernight.client.modules.render.Xray.XrayModule;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public class VisGraphMixin {
    private static final FreecamModule freecam = ClientContext.getModule(FreecamModule.class);

    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void suppressOpaqueBlockMarking(BlockPos blockPos, CallbackInfo callback) {
        if (XrayModule.getInstance().isToggled() || freecam.isToggled()) {
            callback.cancel();
        }
    }
}