package evernight.mixin.mods.xaero;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides Xaero minimap waypoint markers without touching waypoint data.
 */
@Pseudo
@Mixin(targets = "xaero/hud/minimap/waypoint/render/WaypointMapRenderer", remap = false)
public class XaeroWaypointMapRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideWaypointMarkers(CallbackInfoReturnable<Boolean> callback) {
        if (CoordinateHiderModule.shouldHideMinimapWaypoints()) {
            callback.setReturnValue(false);
        }
    }
}
