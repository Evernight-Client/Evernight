package evernight.client.modules.render.ViewClip;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.input.MouseHandlerOnScrollEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import evernight.client.utils.core.FrameTiming;
import evernight.client.utils.core.KeyUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class ViewClipModule extends Module {
    public final Setting<SkipChoice> skipSetting;
    public final Setting<Float> rangeSetting;
    public final Setting<Boolean> smoothSetting;
    public final Setting<Boolean> scrollSetting;
    public final ClampedInterpolatedFloat clipAnimation;
    private float scrollTargetOffset;
    private float scrollOffset;

    public ViewClipModule() {
        super(
                "ViewClip",
                ModuleCategory.RENDER);
        this.skipSetting = this.add(new EnumSetting<>("Skip", ViewClipModule.SkipChoice.NONE));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 0.1F, 10.0F));
        this.smoothSetting = this.add(new BooleanSetting("Smooth", true));
        this.scrollSetting = this.add(new BooleanSetting("Scroll", false));
        this.clipAnimation = new ClampedInterpolatedFloat(1.3F, true);
        this.setDrawn(false);
    }

    public float getCameraDistance() {
        float distance = (this.rangeSetting.getValue()) + this.scrollOffset;
        return Mth.clamp(distance, 0.1F, 1e+01F);
    }

    @EventHandler
    private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
        Boolean enabled = this.scrollSetting.getValue();
        if (!enabled) {
            return;
        }
        Camera camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera();
        if (!camera.isDetached()) {
            return;
        }
        if (!KeyUtils.isPhysicalKeyDown(341)) {
            return;
        }
        float currentTarget = this.scrollTargetOffset;
        float nextTarget = currentTarget - (float) event.getVerticalOffset();
        Float minimumRangeValue = this.rangeSetting.getValue();
        float minimumOffset = -minimumRangeValue;
        float maximum = (this.rangeSetting.getMaximumValue());
        Float currentRangeValue = this.rangeSetting.getValue();
        float maximumOffset = maximum - currentRangeValue;
        this.scrollTargetOffset = Mth.clamp(nextTarget, minimumOffset, maximumOffset);
        event.cancel();
    }

    public boolean shouldSkipCameraType(CameraType cameraType) {
        if (!this.isToggled()) {
            return false;
        }
        Setting<SkipChoice> skip = this.skipSetting;
        if (skip.getValue() == ViewClipModule.SkipChoice.NONE) {
            return false;
        }
        return this.skipSetting.getValue() == ViewClipModule.SkipChoice.FRONT
                ? cameraType == CameraType.THIRD_PERSON_FRONT : cameraType == CameraType.THIRD_PERSON_BACK;
    }

    @EventHandler
    private void onRenderFrame(WorldRenderEvent.Main event) {
        FrameTiming timing = ClientContext.frameTiming;
        float frameFactor = timing.getFrameFactor(1.0F);
        float currentOffset = this.scrollOffset;
        float difference = this.scrollTargetOffset - this.scrollOffset;
        double exponent = -1e+01F * frameFactor;
        this.scrollOffset = currentOffset + difference * (1.0F - (float) Math.exp(exponent));
        Camera camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera();
        if (camera.isDetached()) {
            ClampedInterpolatedFloat animation = this.clipAnimation;
            animation.setTarget(true);
        } else {
            ClampedInterpolatedFloat animation = this.clipAnimation;
            animation.setValue(0.0F);
            this.scrollTargetOffset = this.scrollOffset = 0.0F;
        }
    }

    public enum SkipChoice implements NamedEntry {
        NONE("None"),
        FRONT("Front"),
        BACK("Back");
        private final String displayName;

        SkipChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}