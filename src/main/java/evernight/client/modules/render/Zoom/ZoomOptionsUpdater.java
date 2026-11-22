package evernight.client.modules.render.Zoom;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.render.util.GameOptionsOverrides;
import evernight.client.settings.core.Setting;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

public class ZoomOptionsUpdater implements ClientAccess {
    private final ZoomModule zoomModule;

    public ZoomOptionsUpdater(ZoomModule zoomModule) {
        this.zoomModule = zoomModule;
    }

    @EventHandler
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        ZoomModule fovModule = this.zoomModule;
        int fov = fovModule.calculateFov();
        OptionInstance fovOption = ClientAccess.evernightMinecraft.options.fov();
        Integer fovValue = fov;
        GameOptionsOverrides.forceSetOptionValue(fovOption, fovValue);
        Options cameraOptions = ClientAccess.evernightMinecraft.options;
        ZoomModule toggleModule = this.zoomModule;
        boolean smoothCamera = false;
        if (toggleModule.isToggled()) {
            Setting<Boolean> smoothCameraSetting = this.zoomModule.smoothCameraSetting;
            smoothCamera = (smoothCameraSetting.getValue());
        }
        cameraOptions.smoothCamera = smoothCamera;
        if (fov >= this.zoomModule.baselineFov) {
            ZoomModule currentToggleModule = this.zoomModule;
            if (!currentToggleModule.isToggled()) {
                EventBus bus = ClientAccess.eventBus;
                bus.unsubscribe(this);
                this.zoomModule.fovUpdateActive = false;
                return;
            }
        }
        this.zoomModule.fovUpdateActive = true;
    }
}