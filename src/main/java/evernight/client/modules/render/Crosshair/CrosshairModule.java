package evernight.client.modules.render.Crosshair;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import java.awt.*;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class CrosshairModule extends Module {
    public final Setting<Boolean> thirdPersonSetting;
    public final Setting<Integer> scaleSetting;
    final Setting<Integer> lengthSetting;
    private final Setting<CrosshairMode> modeSetting;
    private final Setting<Integer> shadowSetting;
    private final Setting<Float> amplitudeSetting;
    private final Setting<Integer> gapSetting;
    private final Setting<Integer> widthSetting;
    private final Setting<Boolean> elementsSetting;
    private final Setting<Boolean> topSetting;
    private final Setting<Boolean> centerSetting;
    private final Setting<Color> colorSetting;
    private final ClampedInterpolatedFloat movementAnimation;

    public CrosshairModule() {
        super("Crosshair", ModuleCategory.RENDER);
        this.modeSetting = this.add(new EnumSetting<>("Mode", CrosshairMode.CUSTOM));
        this.thirdPersonSetting = this.add(new BooleanSetting("ThirdPerson", true));
        this.scaleSetting = this.add(new NumberSetting<>("Scale", 2, 1, 4, ignored -> this.modeSetting.getValue() == CrosshairMode.VANILLA));
        this.shadowSetting = this.add(new NumberSetting<>("Shadow", 1, 0, 3, ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM));
        this.amplitudeSetting = this.add(new NumberSetting<>("Amplitude", 2.0F, 0.0F, 8.0F, ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM));
        this.lengthSetting = this.add(new NumberSetting<>("Length", 2, 0, 16, ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM));
        Predicate<Object> gapVisibility = ignored -> !this.lengthSetting.isAtMinimum();
        this.gapSetting = this.add(new NumberSetting<>("Gap", 2, 0, 16, gapVisibility));
        this.widthSetting = this.add(new NumberSetting<>("Width", 1, 1, 5, ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM));
        this.elementsSetting = this.add(new BooleanSetting("Elements", false, ignored -> this.modeSetting.getValue() == CrosshairMode.CUSTOM).markAsGroup().markVisibilityDependency());
        this.topSetting = this.add(new BooleanSetting("Top", true).dependsOn(this.elementsSetting));
        this.centerSetting = this.add(new BooleanSetting("Center", true).dependsOn(this.elementsSetting));
        this.colorSetting = this.add(new ColorSetting("Color", new Color(-2828, true)).dependsOn(this.elementsSetting));
        this.movementAnimation = new ClampedInterpolatedFloat(2.0F, true);
        Setting<Float> amplitudeLabel = this.amplitudeSetting;
        SettingLabelCondition minimumLabel = SettingLabelCondition.MIN;
        amplitudeLabel.setBoundaryLabel("None", minimumLabel);
        this.shadowSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
        this.lengthSetting.setBoundaryLabel("None", SettingLabelCondition.MIN);
        this.setDrawn(false);
    }

    @EventHandler(priority = -200)
    private void onRenderHud(HudRenderEvent.Unconditional event) {
        if (this.modeSetting.getValue() != CrosshairMode.CUSTOM) return;
        Identifier debugCrosshair = DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR;
        DebugScreenEntryList debugEntries = ClientAccess.evernightMinecraft.debugEntries;
        boolean debugCrosshairEnabled = debugEntries.isCurrentlyEnabled(debugCrosshair);
        if (this.isWorldUnavailable() || ClientAccess.evernightMinecraft.options.hideGui || debugCrosshairEnabled) {
            return;
        }
        Camera camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera();
        if (camera.isDetached()) {
            Setting<Boolean> thirdPerson = this.thirdPersonSetting;
            if (!(thirdPerson.getValue())) {
                return;
            }
        }
        Setting<Integer> gapSetting = this.gapSetting;
        float gap = (gapSetting.getValue()).intValue();
        Setting<Float> amplitudeCheck = this.amplitudeSetting;
        if (!amplitudeCheck.isAtMinimum()) {
            ClampedInterpolatedFloat targetAnimation = this.movementAnimation;
            Vec2 movement = ClientAccess.evernightMinecraft.player.input.moveVector;
            boolean moving = movement.lengthSquared() > 0.0F;
            if (!moving) {
                Input keys = ClientAccess.evernightMinecraft.player.input.keyPresses;
                moving = keys.jump();
            }
            targetAnimation.setTarget(moving);
            float movementAmplitude = this.amplitudeSetting.getValue();
            ClampedInterpolatedFloat currentAnimation = this.movementAnimation;
            gap += movementAmplitude * currentAnimation.updateAndGet();
        }
        float guiScale = (ClientAccess.evernightMinecraft.getWindow()).getGuiScale();
        (event.getPoseStack()).pushPose();
        PoseStack scalePose = event.getPoseStack();
        float scaleX = 1.0F / guiScale;
        float scaleY = 1.0F / guiScale;
        float scaleZ = 1.0F / guiScale;
        scalePose.scale(scaleX, scaleY, scaleZ);
        Setting<Integer> shadowCheck = this.shadowSetting;
        if (!shadowCheck.isAtMinimum()) {
            PoseStack shadowPose = event.getPoseStack();
            shadowPose.pushPose();
            float shadowX = (this.shadowSetting.getValue()).intValue();
            float shadowY = (this.shadowSetting.getValue()).intValue();
            shadowPose.translate(shadowX, shadowY, 0.0F);
            PoseStack shadowDrawPose = event.getPoseStack();
            float shadowLength = (this.lengthSetting.getValue()).intValue();
            Setting<Integer> shadowWidthSetting = this.widthSetting;
            Integer shadowWidthValue = shadowWidthSetting.getValue();
            float shadowWidth = shadowWidthValue.intValue();
            float shadowHalfWidth = shadowWidth / 2.0F;
            Setting<Color> shadowColorSetting = this.colorSetting;
            Color shadowBaseColor = shadowColorSetting.getValue();
            Color shadowColor = ColorUtils.darken(shadowBaseColor, 0.25F);
            this.drawCrosshair(shadowDrawPose, shadowLength, shadowHalfWidth, gap, shadowColor);
            shadowPose.popPose();
        }
        PoseStack crosshairPose = event.getPoseStack();
        Setting<Integer> lengthSetting = this.lengthSetting;
        Integer lengthValue = lengthSetting.getValue();
        float length = lengthValue.intValue();
        Setting<Integer> widthSetting = this.widthSetting;
        Integer widthValue = widthSetting.getValue();
        float width = widthValue.intValue();
        float halfWidth = width / 2.0F;
        Setting<Color> colorSetting = this.colorSetting;
        Color color = colorSetting.getValue();
        this.drawCrosshair(crosshairPose, length, halfWidth, gap, color);
        PoseStack popPose = event.getPoseStack();
        popPose.popPose();
    }

    private void drawCrosshair(PoseStack pose, float length, float halfWidth, float gap, Color color) {
        int screenWidth = (ClientAccess.evernightMinecraft.getWindow()).getScreenWidth();
        int centerX = screenWidth / 2;
        int screenHeight = (ClientAccess.evernightMinecraft.getWindow()).getScreenHeight();
        int centerY = screenHeight / 2;
        double fractionalWidth = halfWidth;
        if (Math.ceil(fractionalWidth) - halfWidth != 0.0) {
            gap += 0.5F;
        } else {
            centerX--;
            centerY++;
        }
        Setting<Boolean> center = this.centerSetting;
        if (center.getValue()) {
            GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY - halfWidth, centerX + halfWidth, centerY + halfWidth, color);
        }
        if (length != 0.0F) {
            Setting<Boolean> top = this.topSetting;
            if (top.getValue()) {
                GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY - gap - length, centerX + halfWidth, centerY - gap, color);
            }
            GuiDrawUtils.fillRect(pose, centerX + gap, centerY - halfWidth, centerX + gap + length, centerY + halfWidth, color);
            GuiDrawUtils.fillRect(pose, centerX - gap - length, centerY - halfWidth, centerX - gap, centerY + halfWidth, color);
            GuiDrawUtils.fillRect(pose, centerX - halfWidth, centerY + gap, centerX + halfWidth, centerY + gap + length, color);
        }
    }

    @Override
    public void fromJson(JsonElement json) {
        JsonObject object = json.getAsJsonObject();
        JsonObject settings = object.getAsJsonObject("settings");
        if (settings != null && !settings.has("Mode") && settings.has("Vanilla")) {
            JsonObject migrated = object.deepCopy();
            migrated.getAsJsonObject("settings").addProperty("Mode", settings.get("Vanilla").getAsBoolean() ? "Vanilla" : "Custom");
            super.fromJson(migrated);
        } else {
            super.fromJson(json);
        }
    }

    public boolean shouldHideVanillaCrosshair() {
        return this.isToggled() && this.modeSetting.getValue() == CrosshairMode.CUSTOM;
    }

    public enum CrosshairMode implements NamedEntry {
        VANILLA("Vanilla"), CUSTOM("Custom");
        private final String name;

        CrosshairMode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }
}