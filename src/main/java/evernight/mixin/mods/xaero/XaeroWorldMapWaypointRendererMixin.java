package evernight.mixin.mods.xaero;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides Xaero world map waypoint markers without touching waypoint data.
 */
@Pseudo
@Mixin(targets = "xaero/map/mods/gui/WaypointRenderer", remap = false)
public class XaeroWorldMapWaypointRendererMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideWaypointMarkers(CallbackInfoReturnable<Boolean> callback) {
        if (CoordinateHiderModule.shouldHideWorldMapWaypoints()) {
            callback.setReturnValue(false);
        }
    }
}
