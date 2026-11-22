package evernight.client.modules.render.Animations;

import evernight.client.ClientAccess;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.Vec3Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.SelectedSlotChangeEvent;
import evernight.client.events.render.CameraSetupEvent;
import evernight.client.events.render.HudRenderEvent;

public class AnimationsModule extends Module {
    /**
     * Hotbar geometry, matching {@code Gui#renderItemHotbar}.
     */
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_SLOT_SPACING = 20;
    private static final int HOTBAR_SELECTION_WIDTH = 24;
    private static final int HOTBAR_SELECTION_HEIGHT = 23;
    private static final float HOTBAR_SELECTION_EXPANSION = 5.0F;
    public final Setting<Boolean> crystalsSetting;
    public final Setting<Float> floatFactorSetting;
    public final Setting<Float> rotationSpeedSetting;
    public final Setting<Float> crystalScaleSetting;
    public final Setting<Boolean> playersSetting;
    public final Setting<Boolean> staticSetting;
    public final Setting<Boolean> sneakSetting;
    public final Setting<Float> playerScaleSetting;
    private final Setting<Boolean> smoothCameraSetting;
    private final Setting<Boolean> partsSetting;
    private final Setting<Boolean> innerSetting;
    private final Setting<Boolean> outerSetting;
    private final Setting<Boolean> coreSetting;
    private final Setting<Boolean> bottomSetting;
    private final Setting<Boolean> hotbarSetting;
    private final Setting<Integer> hotbarLengthSetting;
    private final Setting<Color> hotbarColorSetting;
    private int hotbarSelectedSlot = -1;
    private long hotbarAnimationStartedAt;
    private boolean verticalCameraCatchUp;
    private boolean horizontalCameraCatchUp;
    private Vec3 cameraPosition;
    private Vec3 previousCameraPosition;

    public AnimationsModule() {
        super("Animations", ModuleCategory.RENDER);
        this.smoothCameraSetting = this.add(new BooleanSetting("SmoothCamera", false));
        this.crystalsSetting = this.add(new BooleanSetting("Crystals", false).markVisibilityDependency());
        this.floatFactorSetting = this.add(new NumberSetting<>("FloatFactor", 1.0F, 0.0F, 1.0F).dependsOn(this.crystalsSetting));
        this.rotationSpeedSetting = this.add(new NumberSetting<>("RotationSpeed", 1.0F, 0.0F, 6.0F).dependsOn(this.crystalsSetting));
        this.crystalScaleSetting = this.add(new NumberSetting<>("CrystalScale", 1.0F, 0.0F, 2.0F).dependsOn(this.crystalsSetting));
        this.partsSetting = this.add(new BooleanSetting("Parts", false).markAsGroup().markVisibilityDependency().dependsOn(this.crystalsSetting));
        this.innerSetting = this.add(new BooleanSetting("Inner", true).dependsOn(this.crystalsSetting, this.partsSetting));
        this.outerSetting = this.add(new BooleanSetting("Outer", true).dependsOn(this.crystalsSetting, this.partsSetting));
        this.coreSetting = this.add(new BooleanSetting("Core", true).dependsOn(this.crystalsSetting, this.partsSetting));
        this.bottomSetting = this.add(new BooleanSetting("Bottom", true).dependsOn(this.crystalsSetting, this.partsSetting));
        this.playersSetting = this.add(new BooleanSetting("Players", false).markVisibilityDependency());
        this.staticSetting = this.add(new BooleanSetting("Static", false).dependsOn(this.playersSetting));
        this.sneakSetting = this.add(new BooleanSetting("Sneak", false).dependsOn(this.playersSetting));
        this.playerScaleSetting = this.add(new NumberSetting<>("PlayerScale", 1.0F, 0.0F, 2.0F).dependsOn(this.playersSetting));
        Setting<Boolean> callbackSetting = this.crystalsSetting;
        Runnable callback = this::resetCrystalModelVisibility;
        callbackSetting.setChangeCallback(callback);
        this.hotbarSetting = this.add(new BooleanSetting("Hotbar", true));
        this.hotbarLengthSetting = this.add(new NumberSetting<>("Length", 250, 50, 1000).withUnit("ms"));
        this.hotbarColorSetting = this.add(new ColorSetting("Color", new Color(255, 255, 255, 160)));
    }

    public boolean shouldHideCrystalPart(int partIndex) {
        if (!this.isToggled()) {
            return false;
        }
        return switch (partIndex) {
            case 0 -> this.shouldHideCrystalBottom();
            case 1 -> this.shouldHideCrystalOuter();
            case 2 -> this.shouldHideCrystalInner();
            case 3 -> this.shouldHideCrystalCore();
            default -> false;
        };
    }

    public boolean isPlayerScalingEnabled() {
        if (this.isToggled()) {
            Boolean playersEnabled = this.playersSetting.getValue();
            if (playersEnabled) {
                float scale = this.playerScaleSetting.getValue();
                return scale != 1.0F;
            }
        }
        return false;
    }

    public void resetCrystalModelVisibility() {
        EndCrystal crystal = new EndCrystal(EntityType.END_CRYSTAL, ClientAccess.evernightMinecraft.level);
        EntityRenderDispatcher dispatcherSnapshot = ClientAccess.evernightMinecraft.getEntityRenderDispatcher();
        EndCrystalRenderer renderer = (EndCrystalRenderer) dispatcherSnapshot.getRenderer(crystal);
        renderer.model.cube.skipDraw = false;
        renderer.model.innerGlass.skipDraw = false;
        renderer.model.outerGlass.skipDraw = false;
    }

    public boolean shouldHideCrystalBottom() {
        return this.isCrystalAnimationEnabled() && !this.bottomSetting.getValue();
    }

    public boolean shouldHideCrystalOuter() {
        return this.isCrystalAnimationEnabled() && !this.outerSetting.getValue();
    }

    public boolean isStaticPlayerAnimationEnabled() {
        return this.isToggled() && this.playersSetting.getValue() && this.staticSetting.getValue();
    }

    public boolean isSneakingAnimationEnabled() {
        return this.isToggled() && this.playersSetting.getValue() && this.sneakSetting.getValue();
    }

    public boolean shouldHideCrystalCore() {
        return this.isCrystalAnimationEnabled() && !this.coreSetting.getValue();
    }

    public boolean isCrystalAnimationEnabled() {
        return this.isToggled() && this.crystalsSetting.getValue();
    }

    public boolean shouldHideCrystalInner() {
        return this.isCrystalAnimationEnabled() && !this.innerSetting.getValue();
    }

    @Override
    public void onDisable() {
        this.resetCrystalModelVisibility();
    }

    private boolean updateCameraCatchUp(boolean previousCatchUp, double distance, double catchUpThreshold) {
        if (distance > catchUpThreshold || PlayerStateUtils.isFallFlying()) {
            return true;
        }
        return !(distance < 0.1D) && previousCatchUp;
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 playerPosition = player.position();
        Boolean smoothCameraEnabled = this.smoothCameraSetting.getValue();
        if (smoothCameraEnabled && this.cameraPosition != null && this.previousCameraPosition != null) {
            this.previousCameraPosition = this.cameraPosition;
            Vec3 cameraPositionSnapshot = this.cameraPosition;
            double horizontalDistance = Vec3Utils.horizontalDistance(cameraPositionSnapshot, playerPosition);
            double verticalDelta = playerPosition.y - this.cameraPosition.y;
            double verticalDistance = Math.abs(verticalDelta);
            boolean previousHorizontalCatchUp = this.horizontalCameraCatchUp;
            this.horizontalCameraCatchUp = this.updateCameraCatchUp(previousHorizontalCatchUp, horizontalDistance, 3.0D);
            boolean previousVerticalCatchUp = this.verticalCameraCatchUp;
            this.verticalCameraCatchUp = this.updateCameraCatchUp(previousVerticalCatchUp, verticalDistance, 3.5D);
            double horizontalFraction = Math.min(1.0D, horizontalDistance) / horizontalDistance;
            if (Double.isNaN(horizontalFraction) || horizontalDistance > 64.0D) {
                horizontalFraction = 1.0D;
            } else if (this.horizontalCameraCatchUp) {
                horizontalFraction = Math.min(5.0D, horizontalDistance) / horizontalDistance;
            }
            double verticalFraction = Math.min(0.7D, verticalDistance) / verticalDistance;
            if (Double.isNaN(verticalFraction) || this.verticalCameraCatchUp) {
                verticalFraction = 1.0D;
            }
            this.cameraPosition = new Vec3(Mth.lerp(horizontalFraction, this.cameraPosition.x, playerPosition.x),
                    Mth.lerp(verticalFraction, this.cameraPosition.y, playerPosition.y),
                    Mth.lerp(horizontalFraction, this.cameraPosition.z, playerPosition.z));
        } else {
            this.previousCameraPosition = this.cameraPosition = playerPosition;
        }
    }

    @EventHandler(priority = 100)
    private void onCameraSetupEvent(CameraSetupEvent event) {
        if (!this.smoothCameraSetting.getValue()) {
            return;
        }
        Options options = ClientAccess.evernightMinecraft.options;
        CameraType cameraType = options.getCameraType();
        if (!cameraType.isFirstPerson() || this.cameraPosition == null || this.previousCameraPosition == null) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player.getAbilities().flying) {
            return;
        }
        float partialTick = event.getPartialTick();
        Vec3 currentPosition = this.cameraPosition;
        Vec3 interpolatedPosition = this.previousCameraPosition.lerp(currentPosition, partialTick);
        GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        float currentEyeHeight = camera.eyeHeight;
        float previousEyeHeight = camera.eyeHeightOld;
        float eyeHeight = Mth.lerp(partialTick, previousEyeHeight, currentEyeHeight);
        Vec3 cameraPosition = interpolatedPosition.add(0.0, eyeHeight, 0.0);
        event.setPosition(cameraPosition);
        event.cancel();
    }

    @EventHandler
    private void onSelectedSlotChange(SelectedSlotChangeEvent event) {
        this.hotbarSelectedSlot = event.getSlot();
        this.hotbarAnimationStartedAt = System.currentTimeMillis();
    }

    @EventHandler
    private void onHotbarHudRender(HudRenderEvent.Unconditional event) {
        if (!this.hotbarSetting.getValue() || this.hotbarSelectedSlot < 0
                || ClientAccess.evernightMinecraft.player == null
                || ClientAccess.evernightMinecraft.options.hideGui) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.hotbarAnimationStartedAt;
        int length = this.hotbarLengthSetting.getValue();
        if (elapsed < 0L || elapsed > length) {
            return;
        }
        float progress = (float) elapsed / (float) Math.max(length, 1);
        Color baseColor = this.hotbarColorSetting.getValue();
        int alpha = (int) (baseColor.getAlpha() * (1.0F - progress));
        if (alpha <= 0) {
            return;
        }
        int guiWidth = ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth();
        int guiHeight = ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight();
        // Same geometry as Gui#renderItemHotbar: a centered 182x22 hotbar, a 24x23 selection frame
        // per slot and 20 pixels of spacing between slots.
        int hotbarLeft = guiWidth / 2 - HOTBAR_WIDTH / 2;
        float selectionLeft = hotbarLeft - 1 + this.hotbarSelectedSlot * HOTBAR_SLOT_SPACING;
        float selectionTop = guiHeight - HOTBAR_HEIGHT - 1;
        float centerX = selectionLeft + HOTBAR_SELECTION_WIDTH / 2.0F;
        float centerY = selectionTop + HOTBAR_SELECTION_HEIGHT / 2.0F;
        float halfWidth = HOTBAR_SELECTION_WIDTH / 2.0F + HOTBAR_SELECTION_EXPANSION * progress;
        float halfHeight = HOTBAR_SELECTION_HEIGHT / 2.0F + HOTBAR_SELECTION_EXPANSION * progress;
        GuiDrawUtils.drawRectOutline(event.getPoseStack(), centerX - halfWidth, centerY - halfHeight,
                centerX + halfWidth, centerY + halfHeight, ColorUtils.withAlpha(baseColor, alpha));
        GuiDrawUtils.flush();
    }
}