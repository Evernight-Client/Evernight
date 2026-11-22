package evernight.client.modules.render.FreeLook;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.player.Freecam.FreecamModule;
import evernight.client.render.util.GameOptionsOverrides;
import evernight.client.render.util.RenderUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.InterpolatedFloat;
import evernight.client.utils.movement.EntityMotionAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector2f;
import evernight.client.events.core.EventHandler;
import evernight.client.events.input.MouseHandlerOnScrollEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.ui.ScreenChangeEvent;

public class FreeLookModule extends Module {
    private static FreecamModule freecamModule = ClientContext.getModule(FreecamModule.class);
    public final Setting<Boolean> scrollSetting;
    private final InterpolatedFloat cameraDistanceAnimation;
    private final Vector2f cameraRotation;
    private CameraType cameraType;
    private float cameraDistance;

    public FreeLookModule() {
        super("FreeLook", ModuleCategory.RENDER);
        this.cameraDistanceAnimation = new InterpolatedFloat(2.0F, true);
        this.cameraRotation = new Vector2f();
        Boolean scrollDefault = false;
        String scrollName = "Scroll";
        BooleanSetting scroll = new BooleanSetting(scrollName, scrollDefault);
        this.scrollSetting = this.add(scroll);
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        freecamModule = registry.getModule(FreecamModule.class);
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable()) {
            this.disable();
            return;
        }
        this.cameraDistance = 1e+02F;
        this.cameraDistanceAnimation.setValue(0.0F);
        Options options = ClientAccess.evernightMinecraft.options;
        CameraType selectedCameraType;
        if (options.getCameraType() == CameraType.FIRST_PERSON) {
            selectedCameraType = CameraType.THIRD_PERSON_BACK;
        } else {
            selectedCameraType = ClientAccess.evernightMinecraft.options.getCameraType();
        }
        this.cameraType = selectedCameraType;
        GameOptionsOverrides.registerCameraOverride(this);
        Vector2f rotation = this.cameraRotation;
        float yaw = ClientAccess.evernightMinecraft.player.getYRot();
        float pitch = ClientAccess.evernightMinecraft.player.getXRot();
        rotation.set(yaw, pitch);
        if (ClientAccess.evernightMinecraft.player instanceof EntityMotionAccess cameraPlayer) {
            float cameraYaw = this.cameraRotation.x;
            cameraPlayer.setCameraYaw(cameraYaw);
            float cameraPitch = this.cameraRotation.y;
            cameraPlayer.setCameraPitch(cameraPitch);
        }
    }

    @Override
    public void onDisable() {
        GameOptionsOverrides.unregisterCameraOverride(this);
        if (this.isWorldUnavailable()) {
            return;
        }
        LocalPlayer yawPlayer = ClientAccess.evernightMinecraft.player;
        Vector2f yawRotation = this.cameraRotation;
        float yaw = yawRotation.x();
        yawPlayer.setYRot(yaw);
        LocalPlayer previousYawBobPlayer = ClientAccess.evernightMinecraft.player;
        LocalPlayer yawBobPlayer = ClientAccess.evernightMinecraft.player;
        LocalPlayer previousYawPlayer = ClientAccess.evernightMinecraft.player;
        Vector2f previousYawRotation = this.cameraRotation;
        previousYawBobPlayer.yBobO = yawBobPlayer.yBob = previousYawPlayer.yRotO = previousYawRotation.x();
        LocalPlayer pitchPlayer = ClientAccess.evernightMinecraft.player;
        Vector2f pitchRotation = this.cameraRotation;
        float pitch = pitchRotation.y();
        pitchPlayer.setXRot(pitch);
        LocalPlayer previousPitchBobPlayer = ClientAccess.evernightMinecraft.player;
        LocalPlayer pitchBobPlayer = ClientAccess.evernightMinecraft.player;
        LocalPlayer previousPitchPlayer = ClientAccess.evernightMinecraft.player;
        Vector2f previousPitchRotation = this.cameraRotation;
        previousPitchBobPlayer.xBobO = pitchBobPlayer.xBob = previousPitchPlayer.xRotO = previousPitchRotation.y();
        Camera camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera();
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        LocalPlayer cameraPlayer = ClientAccess.evernightMinecraft.player;
        camera.setup(level, cameraPlayer, false, false, 1.0F);
    }

    public float getCameraDistanceOffset() {
        float offset = this.cameraDistanceAnimation.getValue();
        return offset / 1e+01F;
    }

    public boolean isScrollEnabled() {
        return this.isToggled() && this.scrollSetting.getValue();
    }

    @EventHandler(priority = -200)
    private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
        FreecamModule freecam = freecamModule;
        if (freecam.isToggled()) {
            return;
        }
        Options options = ClientAccess.evernightMinecraft.options;
        CameraType selectedCameraType = this.cameraType;
        options.setCameraType(selectedCameraType);
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        LocalPlayer pickPlayer = ClientAccess.evernightMinecraft.player;
        double range = ClientAccess.evernightMinecraft.player.blockInteractionRange();
        float partialTick = RenderUtils.getPartialTick();
        minecraft.hitResult = pickPlayer.pick(range, partialTick, false);
    }

    @EventHandler
    private void onMouseHandlerOnScrollEvent(MouseHandlerOnScrollEvent event) {
        Boolean enabled = this.scrollSetting.getValue();
        if (enabled) {
            float previousDistance = this.cameraDistance;
            float scrollDelta = (float) event.getVerticalOffset() * this.cameraDistance;
            this.cameraDistance = previousDistance - scrollDelta * 0.08F;
            float distance = this.cameraDistance;
            this.cameraDistance = Mth.clamp(distance, 1.0F, 2e+02F);
            event.cancel();
        }
    }

    @EventHandler
    private void onMinecraftSetScreenEvent(ScreenChangeEvent.Before event) {
        if (event.getNewScreen() instanceof LevelLoadingScreen) {
            this.disable();
        }
    }

    @EventHandler(priority = 200)
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        if (this.isWorldUnavailable()) {
            return;
        }
        InterpolatedFloat targetAnimation = this.cameraDistanceAnimation;
        float distance = this.cameraDistance;
        float target = distance - 1e+02F;
        targetAnimation.setTarget(target);
        this.cameraDistanceAnimation.updateAndGet();
    }

    public Vector2f getCameraRotation() {
        return this.cameraRotation;
    }
}