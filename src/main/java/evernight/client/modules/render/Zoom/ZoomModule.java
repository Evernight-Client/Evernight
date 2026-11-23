package evernight.client.modules.render.Zoom;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.input.MouseHandlerOnScrollEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.GameOptionsOverrides;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.FloatAnimation;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.util.Mth;

public class ZoomModule extends Module {
    protected final Setting<Boolean> smoothCameraSetting;
    private final Setting<Boolean> smoothSetting;
    private final Setting<Boolean> scrollSetting;
    private final Setting<Integer> amountSetting;
    private final ZoomOptionsUpdater optionsUpdater;
    private final FloatAnimation zoomAnimation;
    private final FloatAnimation scrollAnimation;
    protected int baselineFov;
    protected boolean savedSmoothCamera;
    protected boolean fovUpdateActive;

    public ZoomModule() {
        super(
                "Zoom",
                ModuleCategory.RENDER);
        this.smoothSetting = this.add(new BooleanSetting("Smooth", true));
        this.smoothCameraSetting = this.add(new BooleanSetting("SmoothCamera", true));
        this.scrollSetting = this.add(new BooleanSetting("Scroll", false));
        String amountKey = "Amount";
        this.amountSetting = this.add(new NumberSetting<>(amountKey, 2, 2, 5));
        this.optionsUpdater = new ZoomOptionsUpdater(this);
        this.zoomAnimation = new FloatAnimation();
        this.scrollAnimation = new FloatAnimation();
    }

    protected int calculateFov() {
        float baseline = this.baselineFov;
        Integer divisor = this.amountSetting.getValue();
        int fovDifference = 75 / divisor - this.baselineFov;
        float distance = Math.abs(fovDifference);
        int fov = (int) (baseline - distance * this.getZoomBlend());
        Boolean scrollEnabled = this.scrollSetting.getValue();
        if (!scrollEnabled) {
            return fov;
        }
        float adjustedFov = fov;
        FloatAnimation animation = this.scrollAnimation;
        adjustedFov -= animation.getValue();
        return (int) Mth.clamp(adjustedFov, 1e+01F, 1.5e+02F);
    }

    public float getZoomBlend() {
        FloatAnimation animation = this.zoomAnimation;
        double progress = 1.0F - animation.getValue();
        return (float) (1.0D - Math.pow(progress, 4.0D));
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable()) {
            this.disable();
            return;
        }
        if (!this.fovUpdateActive) {
            Options options = ClientAccess.evernightMinecraft.options;
            OptionInstance fov = options.fov();
            this.baselineFov = ((Integer) fov.get());
            this.savedSmoothCamera = options.smoothCamera;
            this.fovUpdateActive = true;
            EventBus bus = ClientAccess.eventBus;
            bus.subscribe(this.optionsUpdater);
        }
        FloatAnimation animation = this.zoomAnimation;
        long duration = this.getSmoothingDuration(200);
        animation.setTarget(1.0F, duration);
    }

    @Override
    public void onDisable() {
        long duration = this.getSmoothingDuration(100);
        FloatAnimation primaryAnimation = this.zoomAnimation;
        primaryAnimation.setTarget(0.0F, duration);
        FloatAnimation scroll = this.scrollAnimation;
        scroll.setTarget(0.0F, duration);
        // Without smoothing there is nothing to animate, and without a world the updater must not
        // keep the zoomed FOV around, so restore the captured values right away in both cases.
        if (duration == 0L || this.isWorldUnavailable()) {
            this.restoreFovOverride();
        }
    }

    /**
     * Puts the FOV and smooth-camera option back to the values captured when zoom started, then
     * releases the run-tick updater. Safe to call repeatedly; does nothing when no override is active.
     */
    protected void restoreFovOverride() {
        if (!this.fovUpdateActive) {
            return;
        }
        Options options = ClientAccess.evernightMinecraft.options;
        OptionInstance fov = options.fov();
        Integer baselineValue = this.baselineFov;
        GameOptionsOverrides.forceSetOptionValue(fov, baselineValue);
        options.smoothCamera = this.savedSmoothCamera;
        ClientAccess.eventBus.unsubscribe(this.optionsUpdater);
        this.fovUpdateActive = false;
    }

    private int getSmoothingDuration(int duration) {
        return !(this.smoothSetting.getValue()) ? 0 : duration;
    }

    @EventHandler
    private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
        if (event.getVerticalOffset() == 0.0) {
            return;
        }
        Boolean enabled = this.scrollSetting.getValue();
        if (!enabled) {
            return;
        }
        float baseline = this.baselineFov;
        Integer divisor = this.amountSetting.getValue();
        int fovDifference = 75 / divisor - this.baselineFov;
        float distance = Math.abs(fovDifference);
        int fov = (int) (baseline - distance * this.getZoomBlend());
        double currentValue = this.scrollAnimation.getValue();
        double delta = event.getVerticalOffset();
        float target = (float) (currentValue + delta * 10.0D);
        float minimum = fov - this.baselineFov;
        float maximum = fov - 10;
        int clampedTarget = (int) Mth.clamp(target, minimum, maximum);
        FloatAnimation targetScroll = this.scrollAnimation;
        float scrollTarget = clampedTarget;
        long duration = this.getSmoothingDuration(100);
        targetScroll.setTarget(scrollTarget, duration);
        event.cancel();
    }

    @EventHandler
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        if (this.isWorldUnavailable()) {
            this.disable();
        }
    }
}