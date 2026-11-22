package evernight.mixin.mods.xaero;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides Xaero minimap waypoint markers drawn in the world.
 */
@Pseudo
@Mixin(targets = "xaero/hud/minimap/waypoint/render/world/WaypointWorldRenderer", remap = false)
public class XaeroWaypointWorldRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideWaypointMarkers(CallbackInfoReturnable<Boolean> callback) {
        if (CoordinateHiderModule.shouldHideMinimapWaypoints()) {
            callback.setReturnValue(false);
        }
    }
}
