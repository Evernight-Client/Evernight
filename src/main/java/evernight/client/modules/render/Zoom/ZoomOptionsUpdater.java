package evernight.client.modules.render.Zoom;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.render.util.GameOptionsOverrides;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Writes the animated zoom FOV into {@link Options#fov()} on every client tick.
 *
 * <p>The updater stays subscribed after {@link ZoomModule#onDisable()} so the FOV can animate back to
 * the value captured when zoom started. Once that animation finished while the module is still off, it
 * restores the captured FOV, restores the smooth-camera option and unsubscribes itself. It never keeps
 * writing options after that point, so the restored values cannot be clobbered by a stale tick.
 */
public class ZoomOptionsUpdater implements ClientAccess {
    private final ZoomModule zoomModule;

    public ZoomOptionsUpdater(ZoomModule zoomModule) {
        this.zoomModule = zoomModule;
    }

    @EventHandler
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        ZoomModule module = this.zoomModule;
        if (!module.fovUpdateActive) {
            ClientAccess.eventBus.unsubscribe(this);
            return;
        }
        if (!module.isToggled()) {
            if (module.isWorldUnavailable() || module.getZoomBlend() <= 0.0F) {
                module.restoreFovOverride();
                return;
            }
            this.applyFov(module);
            return;
        }
        this.applyFov(module);
        Options options = ClientAccess.evernightMinecraft.options;
        options.smoothCamera = module.smoothCameraSetting.getValue();
    }

    private void applyFov(ZoomModule module) {
        OptionInstance fovOption = ClientAccess.evernightMinecraft.options.fov();
        Integer fovValue = module.calculateFov();
        GameOptionsOverrides.forceSetOptionValue(fovOption, fovValue);
    }
}
