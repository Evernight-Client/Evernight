package evernight.client.modules.render.Trails;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.*;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.math.MathConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.mesh.LineMeshRenderer;
import evernight.client.render.shader.MaskShaderRenderer;
import evernight.client.render.shader.RenderTargetUtils;
import evernight.client.render.util.RenderUtils;

public class TrailsModule extends Module {
    final Setting<Boolean> fadeSetting;
    final Setting<Boolean> targetsSetting;
    final Setting<Color> colorSetting;
    private final HashMap<Integer, List<TrailPoint>> trailsByEntityId = new HashMap<>();
    private final Setting<Boolean> renderSetting;
    private final Setting<Boolean> bloomSetting;
    private final Setting<Float> delaySetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Float> fadeDelaySetting;
    private final Setting<Float> fadeDurationSetting;
    private final Setting<Boolean> selfSetting;
    private final Setting<Boolean> playersSetting;
    private final Setting<Boolean> pearlsSetting;
    private final Setting<Boolean> arrowsSetting;
    private final Setting<Boolean> expSetting;
    private final Setting<Double> thresholdSetting;
    private final ElapsedTimer sampleTimer;

    public TrailsModule() {
        super("Trails", ModuleCategory.RENDER);
        this.renderSetting = this.add(new BooleanSetting("Render", true));
        this.bloomSetting = this.add(new BooleanSetting("Bloom", false));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 0.0F, 0.0F, 5.0F).withUnit("s"));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 5.0F));
        this.fadeSetting = this.add(new BooleanSetting("Fade", false).markVisibilityDependency());
        Predicate<Object> fadeDelayVisible = ignored -> this.fadeSetting.isExpanded();
        this.fadeDelaySetting = this.add(new NumberSetting<>("FadeDelay", 1.0F, 0.0F, 10.0F, fadeDelayVisible).withUnit("s"));
        Predicate<Object> fadeDurationVisible = ignored -> this.fadeSetting.isExpanded();
        this.fadeDurationSetting = this.add(new NumberSetting<>("FadeDuration", 0.5F, 0.0F, 10.0F, fadeDurationVisible).withUnit("s"));
        this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency());
        Predicate<Object> selfVisible = ignored -> this.targetsSetting.isExpanded();
        this.selfSetting = this.add(new BooleanSetting("Self", true, selfVisible));
        Predicate<Object> playersVisible = ignored -> this.targetsSetting.isExpanded();
        this.playersSetting = this.add(new BooleanSetting("Players", false, playersVisible));
        Predicate<Object> pearlsVisible = ignored -> this.targetsSetting.isExpanded();
        this.pearlsSetting = this.add(new BooleanSetting("Pearls", false, pearlsVisible));
        Predicate<Object> arrowsVisible = ignored -> this.targetsSetting.isExpanded();
        this.arrowsSetting = this.add(new BooleanSetting("Arrows", false, arrowsVisible));
        Predicate<Object> expVisible = ignored -> this.targetsSetting.isExpanded();
        this.expSetting = this.add(new BooleanSetting("Exp", false, expVisible));
        this.colorSetting = this.add(new ColorSetting("Color", new Color(-1312974337, true)));
        Predicate<Object> thresholdVisible = ignored -> ((ColorSetting) this.colorSetting).isRainbow();
        this.thresholdSetting = this.add(new NumberSetting<>("Threshold", 1.0, 0.1, 3.0, thresholdVisible));
        this.sampleTimer = new ElapsedTimer();
        this.setDrawn(false);
    }

    private long getFadeDelayMillis() {
        float delayValue = (this.fadeDelaySetting.getValue());
        return (long) (delayValue * 1e+03F);
    }

    @Override
    public void onToggle() {
        this.trailsByEntityId.clear();
    }

    @EventHandler(priority = -100)
    private void onBeforeBufferSource(WorldRenderEvent.BeforeBufferSource event) {
        if (!this.isWorldUnavailable()) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            if (player.isDeadOrDying()) {
                this.trailsByEntityId.clear();
            } else {
                Float delaySeconds = this.delaySetting.getValue();
                if (delaySeconds != 0.0F) {
                    ElapsedTimer timerBeforeCheck = this.sampleTimer;
                    double delayValue = (this.delaySetting.getValue()).floatValue();
                    TimeUnit delayUnit = TimeUnit.SECONDS;
                    if (!timerBeforeCheck.hasElapsed(delayValue, delayUnit)) {
                        return;
                    }
                }
                this.sampleTimer.reset();
                int playerTick = ClientAccess.evernightMinecraft.player.tickCount;
                if (playerTick % 4 == 0) {
                    int emptyEntityId = 0;
                    for (Integer entityId : this.trailsByEntityId.keySet()) {
                        List<TrailPoint> points = this.trailsByEntityId.get(entityId);
                        points.removeIf(expiringPoint -> {
                                    Boolean fadeEnabled = this.fadeSetting.getValue();
                                    if (fadeEnabled) {
                                        long pointAgeMillis = System.currentTimeMillis()
                                                - expiringPoint.createdAtMillis;
                                        long fadeDelayMillis = this.getFadeDelayMillis();
                                        return pointAgeMillis > fadeDelayMillis + this.getFadeDurationMillis();
                                    }
                                    return false;
                                }
                        );
                        if (points.isEmpty()) {
                            emptyEntityId = entityId;
                            break;
                        }
                    }
                    if (emptyEntityId != 0) {
                        this.trailsByEntityId.remove(emptyEntityId);
                    }
                }
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                for (Entity entity : level.entitiesForRendering()) {
                    if (this.shouldTrackEntity(entity)) {
                        TrailsModule.TrailPoint point = new TrailsModule.TrailPoint(entity.getPosition(RenderUtils.getPartialTick()));
                        HashMap<Integer, List<TrailPoint>> trailMap = this.trailsByEntityId;
                        int newEntityId = entity.getId();
                        List<TrailPoint> entityTrail = trailMap.computeIfAbsent(newEntityId, entityId -> new ArrayList<>());
                        entityTrail.add(point);
                    }
                }
            }
        }
    }

    private void renderTrail(PoseStack poseStack, List<TrailPoint> points, float lineWidth) {
        LineMeshRenderer.begin(poseStack, lineWidth);
        int redValue = (this.colorSetting.getValue()).getRed();
        int greenValue = (this.colorSetting.getValue()).getGreen();
        int blueValue = (this.colorSetting.getValue()).getBlue();
        float[] baseHsb = Color.RGBtoHSB(redValue, greenValue, blueValue, null);
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            TrailPoint previous = points.get(pointIndex - 1);
            TrailPoint point = points.get(pointIndex);
            LineMeshRenderer.addVertex(previous.position.x, previous.position.y, previous.position.z,
                    this.getPointColor(baseHsb, previous).hashCode(), true, false);
            LineMeshRenderer.addVertex(point.position.x, point.position.y, point.position.z,
                    this.getPointColor(baseHsb, point).hashCode(), true, false);
        }
        LineMeshRenderer.draw();
    }

    private Color getPointColor(float[] baseHsb, TrailsModule.TrailPoint point) {
        double pointCreatedAtMillis = point.createdAtMillis;
        double thresholdFactor = (this.thresholdSetting.getValue());
        double huePeriod = pointCreatedAtMillis / (thresholdFactor * 20.0D);
        float hue = (float) (Math.ceil(huePeriod) % MathConstants.FULL_TURN_DEGREES
                / MathConstants.FULL_TURN_DEGREES);
        long nowMillis = System.currentTimeMillis();
        long pointAgeMillis = nowMillis - point.createdAtMillis;
        float ageAfterFadeDelay = (float) (pointAgeMillis - this.getFadeDelayMillis());
        float fadeFraction = 1.0F - ageAfterFadeDelay / (float) this.getFadeDurationMillis();
        Color alphaColor = this.colorSetting.getValue();
        float rawAlpha = fadeFraction * alphaColor.getAlpha();
        float clampedAlpha = Mth.clamp(rawAlpha, 0.0F, 255.0F);
        float alphaMultiplier = clampedAlpha / 255.0F;
        ColorSetting colorSettingForMode = (ColorSetting) this.colorSetting;
        Color selectedColor;
        if (colorSettingForMode.isRainbow()) {
            float baseSaturation = baseHsb[1];
            float brightness = baseHsb[2];
            selectedColor = Color.getHSBColor(hue, baseSaturation, brightness);
        } else {
            selectedColor = this.colorSetting.getValue();
        }
        Color color = selectedColor;
        Boolean fadeEnabled = this.fadeSetting.getValue();
        if (fadeEnabled) {
            long nowForFadeStart = System.currentTimeMillis();
            long pointAgeAtFadeStart = nowForFadeStart - point.createdAtMillis;
            if (pointAgeAtFadeStart >= this.getFadeDelayMillis()) {
                return ColorUtils.multiplyAlpha(color, alphaMultiplier);
            }
        }
        alphaMultiplier = 1.0F;
        return ColorUtils.multiplyAlpha(color, 1.0F);
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        if (!this.isWorldUnavailable()) {
            Boolean renderEnabled = this.renderSetting.getValue();
            if (renderEnabled) {
                RenderTarget mainTarget = ClientAccess.evernightMinecraft.getMainRenderTarget();
                RenderTarget maskTarget = MaskShaderRenderer.MASK_TARGET;
                Setting<Color> maskColor = this.colorSetting;
                RenderUtils.clearColor(maskTarget, maskColor.getValue());
                RenderUtils.clearDepth(MaskShaderRenderer.MASK_TARGET);
                RenderTargetUtils.setMainRenderTarget(MaskShaderRenderer.MASK_TARGET);
                for (Entry<Integer, List<TrailPoint>> entry : this.trailsByEntityId.entrySet()) {
                    int pointCount = entry.getValue().size();
                    if (pointCount > 2) {
                        PoseStack poseStack = event.getPoseStack();
                        List<TrailPoint> points = entry.getValue();
                        Float width = this.lineWidthSetting.getValue();
                        this.renderTrail(poseStack, points, width);
                    }
                }
                RenderTargetUtils.setMainRenderTarget(mainTarget);
                RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET);
                Boolean bloomEnabled = this.bloomSetting.getValue();
                if (!bloomEnabled) {
                    return;
                }
                Float bloomWidth = this.lineWidthSetting.getValue();
                float effectWidth = 6.0F + bloomWidth;
                MaskShaderRenderer.setParameters(effectWidth, 1.5F, 1.0F, 2.0F);
                MaskShaderRenderer.renderEffect("bloom_mask");
            }
        }
    }

    private boolean shouldTrackEntity(Entity entity) {
        if (entity == ClientAccess.evernightMinecraft.player) {
            Boolean includeSelf = this.selfSetting.getValue();
            if (includeSelf) {
                return true;
            }
        }
        if (entity instanceof Player) {
            Boolean includePlayers = this.playersSetting.getValue();
            if (includePlayers && entity != ClientAccess.evernightMinecraft.player) {
                return true;
            }
        }
        if (entity instanceof ThrownEnderpearl) {
            Boolean includePearls = this.pearlsSetting.getValue();
            if (includePearls) {
                return true;
            }
        }
        if (entity instanceof ThrownExperienceBottle) {
            Boolean includeExperienceBottles = this.expSetting.getValue();
            if (includeExperienceBottles) {
                return true;
            }
        }
        if (entity instanceof Arrow) {
            Boolean includeArrows = this.arrowsSetting.getValue();
            return includeArrows;
        }
        return false;
    }

    private long getFadeDurationMillis() {
        float durationValue = (this.fadeDurationSetting.getValue());
        return (long) (durationValue * 1e+03F);
    }

    private static class TrailPoint {
        private final long createdAtMillis;
        private final Vec3 position;

        private TrailPoint(Vec3 position) {
            this.createdAtMillis = System.currentTimeMillis();
            this.position = position;
        }
    }
}