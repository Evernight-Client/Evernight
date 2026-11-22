package evernight.mixin.mods.xaeroplus;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides XaeroPlus waypoint beacons together with the Xaero minimap waypoint markers.
 */
@Pseudo
@Mixin(targets = "xaeroplus/feature/render/beacon/WaypointBeaconRenderer", remap = false)
public class XaeroPlusWaypointBeaconRendererMixin {
    @Inject(method = "renderHook", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideWaypointBeacons(CallbackInfo callback) {
        if (CoordinateHiderModule.shouldHideMinimapWaypoints()) {
            callback.cancel();
        }
    }
}
