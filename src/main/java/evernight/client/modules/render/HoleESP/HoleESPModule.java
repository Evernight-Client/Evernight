package evernight.client.modules.render.HoleESP;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.world.HoleManager;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.mesh.WorldBoxRenderer;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.MathUtils;
import evernight.client.utils.world.HoleInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Iterator;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class HoleESPModule extends Module {
    final Setting<Boolean> fadeSetting;
    final Setting<Boolean> trappedSetting;
    final Setting<Boolean> safeSetting;
    final Setting<Boolean> unsafeSetting;
    private final Setting<DrawMode> fillSetting;
    private final Setting<DrawMode> lineSetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Float> heightSetting;
    private final Setting<Integer> radiusSetting;
    private final Setting<Boolean> bloomSetting;
    private final Setting<Boolean> hideOwnSetting;
    private final Setting<Float> fadeRadiusSetting;
    private final Setting<Color> trappedFillSetting;
    private final Setting<Color> trappedOutlineSetting;
    private final Setting<Color> safeFillSetting;
    private final Setting<Color> safeOutlineSetting;
    private final Setting<Color> unsafeFillSetting;
    private final Setting<Color> unsafeOutlineSetting;

    public HoleESPModule() {
        super("HoleESP", ModuleCategory.RENDER);
        this.fillSetting = this.add(new EnumSetting<>("Fill", HoleESPModule.DrawMode.GRADIENT));
        this.lineSetting = this.add(new EnumSetting<>("Line", HoleESPModule.DrawMode.SOLID));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F));
        this.heightSetting = this.add(new NumberSetting<>("Height", 0.0F, -1.0F, 1.0F).withUnit("m"));
        String radiusKey = "Radius";
        this.radiusSetting = this.add(new NumberSetting<>(radiusKey, 10, 1, 16).withUnit("m"));
        this.bloomSetting = this.add(new BooleanSetting("Bloom", false));
        this.hideOwnSetting = this.add(new BooleanSetting("HideOwn", false));
        this.fadeSetting = this.add(new BooleanSetting("Fade", false).markVisibilityDependency());
        Predicate<Object> fadeRadiusVisibility = ignored -> this.fadeSetting.isExpanded();
        this.fadeRadiusSetting = this.add(new NumberSetting<>("FadeRadius", 7.0F, 1.0F, 16.0F, fadeRadiusVisibility).withUnit("m"));
        this.trappedSetting = this.add(new BooleanSetting("Trapped", false).markVisibilityDependency());
        Predicate<Object> trappedFillVisibility = ignored -> this.trappedSetting.isExpanded();
        this.trappedFillSetting = this.add(new ColorSetting("TrappedFill", new Color(233622784, true), trappedFillVisibility));
        Predicate<Object> trappedOutlineVisibility = ignored -> this.trappedSetting.isExpanded();
        this.trappedOutlineSetting = this.add(new ColorSetting("TrappedOutline", new Color(-1073745408, true), trappedOutlineVisibility));
        BooleanSetting safe = new BooleanSetting("Safe", true);
        safe.markAsGroup();
        safe.markVisibilityDependency();
        this.safeSetting = this.add(safe);
        Predicate<Object> safeFillVisibility = ignored -> this.safeSetting.isExpanded();
        this.safeFillSetting = this.add(new ColorSetting("SafeFill", new Color(540373280, true), safeFillVisibility));
        Predicate<Object> safeOutlineVisibility = ignored -> this.safeSetting.isExpanded();
        this.safeOutlineSetting = this.add(new ColorSetting("SafeOutline", new Color(-1037662665, true), safeOutlineVisibility));
        this.unsafeSetting = this.add(new BooleanSetting("Unsafe", true).markAsGroup().markVisibilityDependency());
        Predicate<Object> unsafeFillVisibility = ignored -> this.unsafeSetting.isExpanded();
        this.unsafeFillSetting = this.add(new ColorSetting("UnsafeFill", new Color(634257408, true), unsafeFillVisibility));
        Predicate<Object> unsafeOutlineVisibility = ignored -> this.unsafeSetting.isExpanded();
        this.unsafeOutlineSetting = this.add(new ColorSetting("UnsafeOutline", new Color(-1077018624, true), unsafeOutlineVisibility));
    }

    private static BlockPos ownBlockPosition() {
        return PlayerStateUtils.getLocalRoundedBlockPosition();
    }

    @Override
    public String getInfo() {
        HoleManager holeManagerSnapshot = ClientContext.holeManager;
        int holeCount = holeManagerSnapshot.getHoles().size();
        return String.valueOf(holeCount);
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        double renderPartialTick = event.getPartialTick();
        double previousPlayerY = ClientAccess.evernightMinecraft.player.yo;
        double currentPlayerY = ClientAccess.evernightMinecraft.player.getY();
        double interpolatedPlayerY = Mth.lerp(renderPartialTick, previousPlayerY, currentPlayerY);
        Vec3 cameraPosition = (ClientAccess.evernightMinecraft.gameRenderer.getMainCamera()).position();
        AABB ownBlockBounds = new AABB(ownBlockPosition());
        HoleManager holeManagerSnapshot = ClientContext.holeManager;
        Iterator<HoleInfo> holes = holeManagerSnapshot.getHoles().iterator();
        while (holes.hasNext()) {
            HoleInfo hole = holes.next();
            Boolean configuredTrapped = this.trappedSetting.getValue();
            if (!configuredTrapped && hole.trapped()) {
                continue;
            }
            BlockPos rangePosition = hole.pos();
            double rangeRadius = (this.radiusSetting.getValue()).intValue();
            if (rangePosition.closerToCenterThan(cameraPosition, rangeRadius)) {
                AABB playerBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
                AABB ownIntersectionBounds = hole.bounds();
                if (playerBounds.intersects(ownIntersectionBounds)) {
                    Boolean configuredHideOwn = this.hideOwnSetting.getValue();
                    if (configuredHideOwn) {
                        Boolean configuredFade = this.fadeSetting.getValue();
                        if (!configuredFade) {
                            continue;
                        }
                    }
                }
                AABB holeBounds = hole.bounds();
                if (RotationManager.isVisible(holeBounds)) {
                    Color fillColor = this.safeFillSetting.getValue();
                    Color outlineColor = this.safeOutlineSetting.getValue();
                    if (hole.trapped()) {
                        fillColor = this.trappedFillSetting.getValue();
                        outlineColor = this.trappedOutlineSetting.getValue();
                    } else {
                        if (hole.safety() == HoleInfo.Safety.UNSAFE) {
                            fillColor = this.unsafeFillSetting.getValue();
                            outlineColor = this.unsafeOutlineSetting.getValue();
                        }
                    }
                    BlockPos distancePosition = hole.pos();
                    Vec3 holeCenter = distancePosition.getCenter();
                    double cameraDistance = cameraPosition.distanceTo(holeCenter);
                    AABB ownFadeHoleBounds = hole.bounds();
                    boolean fadeOwnHole = ownFadeHoleBounds.intersects(ownBlockBounds)
                            && this.hideOwnSetting.getValue();
                    Boolean configuredFade2 = this.fadeSetting.getValue();
                    if (configuredFade2) {
                        Float configuredFadeRadius = this.fadeRadiusSetting.getValue();
                        if (cameraDistance >= configuredFadeRadius.floatValue() || fadeOwnHole) {
                            float cameraDistanceAsFloat = (float) cameraDistance;
                            float fadeRadiusAsFloat = (this.fadeRadiusSetting.getValue());
                            float radiusAsFloat = (this.radiusSetting.getValue()).intValue();
                            float alphaFactor = 1.0F
                                    - MathUtils.rangeProgress(cameraDistanceAsFloat, fadeRadiusAsFloat, radiusAsFloat);
                            if (fadeOwnHole) {
                                alphaFactor = (float) (interpolatedPlayerY - Math.floor(interpolatedPlayerY));
                            }
                            Color fillAlphaSource = fillColor;
                            fillColor = ColorUtils.withAlpha(
                                    fillColor, (int) (alphaFactor * fillAlphaSource.getAlpha()));
                            Color outlineAlphaSource = outlineColor;
                            outlineColor = ColorUtils.withAlpha(
                                    outlineColor, (int) (alphaFactor * outlineAlphaSource.getAlpha()));
                        }
                    }
                    HoleESPModule.DrawMode configuredFill = this.fillSetting.getValue();
                    switch (configuredFill.ordinal()) {
                        case 1:
                            PoseStack solidFillPoses = event.getPoseStack();
                            AABB solidFillBounds = hole.bounds();
                            BlockPos solidFillPosition = hole.pos();
                            float solidFillBaseY = solidFillPosition.getY();
                            Float configuredHeight = this.heightSetting.getValue();
                            double solidFillMaxY = solidFillBaseY + configuredHeight;
                            WorldBoxRenderer.fillBox(
                                    solidFillPoses, solidFillBounds.setMaxY(solidFillMaxY), fillColor);
                            break;
                        case 2:
                            PoseStack gradientFillPoses = event.getPoseStack();
                            AABB gradientFillBounds = hole.bounds();
                            BlockPos gradientFillPosition = hole.pos();
                            double gradientFillMaxY = gradientFillPosition.getY() + 1;
                            WorldBoxRenderer.fillFadingBox(
                                    gradientFillPoses, gradientFillBounds.setMaxY(gradientFillMaxY), fillColor);
                    }
                    if (this.lineSetting.getValue() != HoleESPModule.DrawMode.NONE) {
                        float lineWidth = (this.lineWidthSetting.getValue());
                        if (this.lineSetting.getValue() == HoleESPModule.DrawMode.GRADIENT) {
                            AABB gradientLineBounds = hole.bounds();
                            BlockPos gradientLinePosition = hole.pos();
                            double gradientLineMaxY = gradientLinePosition.getY() + 1;
                            AABB gradientOutlineBounds = gradientLineBounds.setMaxY(gradientLineMaxY);
                            PoseStack gradientLinePoses = event.getPoseStack();
                            WorldBoxRenderer.outlineFadingBox(
                                    gradientLinePoses, gradientOutlineBounds, outlineColor.hashCode(), lineWidth);
                        }
                        Boolean configuredBloom = this.bloomSetting.getValue();
                        if (configuredBloom) {
                            float distanceToRadius = (this.radiusSetting.getValue()).intValue() - (float) cameraDistance;
                            float maximumBloomWidth = lineWidth + 6.0F;
                            float bloomLineWidth = Math.clamp(distanceToRadius, 1.0F, maximumBloomWidth);
                            Color bloomColor = ColorUtils.multiplyAlpha(outlineColor, 0.15F);
                            PoseStack bloomPoses = event.getPoseStack();
                            AABB bloomBounds = hole.bounds();
                            BlockPos bloomPosition = hole.pos();
                            float bloomBaseY = bloomPosition.getY();
                            Float configuredHeight2 = this.heightSetting.getValue();
                            double bloomMaxY = bloomBaseY + configuredHeight2;
                            WorldBoxRenderer.outlineBox(
                                    bloomPoses, bloomBounds.setMaxY(bloomMaxY), bloomColor, bloomLineWidth);
                            if (this.lineSetting.getValue() == HoleESPModule.DrawMode.GRADIENT) {
                                AABB bloomGradientBounds = hole.bounds();
                                BlockPos bloomGradientPosition = hole.pos();
                                double bloomGradientMaxY = bloomGradientPosition.getY() + 1;
                                AABB bloomGradientOutlineBounds = bloomGradientBounds.setMaxY(bloomGradientMaxY);
                                PoseStack bloomGradientPoses = event.getPoseStack();
                                WorldBoxRenderer.outlineFadingBox(
                                        bloomGradientPoses, bloomGradientOutlineBounds, bloomColor.hashCode(), bloomLineWidth
                                );
                            }
                        }
                        PoseStack outlinePoses = event.getPoseStack();
                        AABB outlineBounds = hole.bounds();
                        BlockPos outlinePosition = hole.pos();
                        float outlineBaseY = outlinePosition.getY();
                        Float configuredHeight3 = this.heightSetting.getValue();
                        double outlineMaxY = outlineBaseY + configuredHeight3;
                        WorldBoxRenderer.outlineBox(
                                outlinePoses, outlineBounds.setMaxY(outlineMaxY), outlineColor, lineWidth);
                    }
                }
            }
        }
    }

    public enum DrawMode implements NamedEntry {
        NONE("None"),
        SOLID("Solid"),
        GRADIENT("Gradient");
        private final String displayName;

        DrawMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}