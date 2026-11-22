package evernight.client.modules.render.Tracers;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.util.RenderUtils;
import evernight.client.settings.options.ColorsSchemeChoice;
import evernight.client.settings.options.TracersHitboxChoice;
import evernight.client.settings.options.TracersModeChoice;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.function.Predicate;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class TracersModule extends Module {
    private static ColorsModule colorsModule = ClientContext.getModule(ColorsModule.class);
    public final Setting<TracersHitboxChoice> hitboxSetting;
    public final Setting<Integer> shadowSetting;
    public final Setting<Float> distanceSetting;
    public final Setting<Float> lineWidthSetting;
    public final Setting<Boolean> stemSetting;
    public final Setting<Boolean> ignoreVisibleSetting;
    final Setting<TracersModeChoice> modeSetting;
    private final Setting<Float> minDistanceSetting;
    private final Setting<Float> maxDistanceSetting;
    private final Setting<Boolean> colorsSetting;
    private final Setting<Color> colorSetting;
    private final Setting<Boolean> distanceColorSetting;
    private final Setting<Boolean> friendColorSetting;
    private final Setting<Boolean> enemyColorSetting;
    private final Setting<Boolean> targetsSetting;
    private final Setting<Boolean> ignoreFriendsSetting;
    private final Setting<Boolean> ignoreNakedsSetting;
    private final LineTracerRenderer lineRenderer;
    private final MarkerTracerRenderer markerRenderer;

    public TracersModule() {
        super(
                "Tracers",
                ModuleCategory.RENDER);
        this.modeSetting = this.add(new EnumSetting<>("Mode", TracersModeChoice.LINES));
        this.hitboxSetting = this.add(new EnumSetting<>("Hitbox", TracersHitboxChoice.HEAD, (ignored -> this.modeSetting.getValue() == TracersModeChoice.LINES)));
        this.minDistanceSetting = this.add(new NumberSetting<>("MinDistance", 0.0F, 0.0F, 128.0F).withUnit("m"));
        this.maxDistanceSetting = this.add(new NumberSetting<>("MaxDistance", 256.0F, 8.0F, 256.0F).withUnit("m"));
        this.shadowSetting = this.add(new NumberSetting<>("Shadow", 1, 0, 3, (ignored -> this.modeSetting.getValue() == TracersModeChoice.MARKER)));
        this.distanceSetting = this.add(new NumberSetting<>("Distance", 16.0F, 8.0F, 50.0F, (ignored -> this.modeSetting.getValue() == TracersModeChoice.MARKER)).withUnit("px"));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 5.0F, (ignored -> this.modeSetting.getValue() == TracersModeChoice.LINES)));
        Predicate<Object> stemVisibility = ignored -> this.modeSetting.getValue() == TracersModeChoice.LINES;
        this.stemSetting = this.add(new BooleanSetting("Stem", true, stemVisibility));
        this.colorsSetting = this.add(new BooleanSetting("Colors", false).markAsGroup());
        this.colorSetting = this.add(new ColorSetting("Color", new Color(-65536, true)).dependsOn(this.colorsSetting));
        this.distanceColorSetting = this.add(new BooleanSetting("DistanceColor", false).dependsOn(this.colorsSetting));
        this.friendColorSetting = this.add(new BooleanSetting("FriendColor", true).dependsOn(this.colorsSetting));
        this.enemyColorSetting = this.add(new BooleanSetting("EnemyColor", true).dependsOn(this.colorsSetting));
        this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup());
        this.ignoreVisibleSetting = this.add(new BooleanSetting("IgnoreVisible", true, (ignored -> this.modeSetting.getValue() == TracersModeChoice.MARKER)).dependsOn(this.targetsSetting));
        this.ignoreFriendsSetting = this.add(new BooleanSetting("IgnoreFriends", false).dependsOn(this.targetsSetting));
        this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.targetsSetting));
        this.lineRenderer = new LineTracerRenderer(this);
        this.markerRenderer = new MarkerTracerRenderer(this);
        Setting<Float> minimumCallbackSetting = this.minDistanceSetting;
        Runnable minimumDistanceCallback = () -> {
            Setting<Float> maximumSetting = this.maxDistanceSetting;
            float maximum = maximumSetting.getValue();
            float minimum = this.minDistanceSetting.getValue();
            maximumSetting.setValue(Math.max(maximum, minimum));
        };
        minimumCallbackSetting.setChangeCallback(minimumDistanceCallback);
        Setting<Float> maximumCallbackSetting = this.maxDistanceSetting;
        Runnable maximumDistanceCallback = () -> {
            Setting<Float> capturedMinimumSetting = this.minDistanceSetting;
            float maximum = this.maxDistanceSetting.getValue();
            float minimum = this.minDistanceSetting.getValue();
            capturedMinimumSetting.setValue(Math.min(maximum, minimum));
        };
        maximumCallbackSetting.setChangeCallback(maximumDistanceCallback);
    }

    public static void bindReferencedModules(ModuleRegistry moduleRegistry) {
        colorsModule = moduleRegistry.getModule(ColorsModule.class);
    }

    public boolean shouldSkip(Player player) {
        GameRenderer gameRenderer = ClientAccess.evernightMinecraft.gameRenderer;
        Vec3 capturedCameraPosition = (gameRenderer.getMainCamera()).position();
        Vec3 playerPosition = player.position();
        double distance = capturedCameraPosition.distanceTo(playerPosition);
        if (player == ClientAccess.evernightMinecraft.player) {
            return true;
        }
        Float maximum = this.maxDistanceSetting.getValue();
        if (distance > maximum.floatValue()) {
            return true;
        }
        Float minimum = this.minDistanceSetting.getValue();
        if (distance < minimum.floatValue()) {
            return true;
        }
        Boolean ignoreFriends = this.ignoreFriendsSetting.getValue();
        if (ignoreFriends) {
            SocialRegistry friendRegistry = ClientContext.socialRegistry;
            if (friendRegistry.isFriendPlayer(player)) {
                return true;
            }
        }
        Boolean ignoreNakeds = this.ignoreNakedsSetting.getValue();
        return ignoreNakeds && !PlayerStateUtils.hasArmor(player);
    }

    public TracerEndpoints getEndpoints(Player player) {
        float tickDelta = RenderUtils.getPartialTick();
        Vec3 position = player.getPosition(tickDelta);
        AABB bounds = player.getBoundingBox();
        if (player.isPassenger()) {
            return TracerEndpoints.riding(player, position);
        }
        if (player.isSleeping()) {
            return TracerEndpoints.sleeping(player);
        }
        double height = bounds.getYsize();
        if (height <= 1.0D) {
            return TracerEndpoints.lowPose(player, position, bounds);
        }
        return TracerEndpoints.standing(player, position);
    }

    @EventHandler
    private void onRenderMarkers(HudRenderEvent event) {
        Setting<TracersModeChoice> mode = this.modeSetting;
        if (mode.getValue() == TracersModeChoice.MARKER) {
            MarkerTracerRenderer markerRenderer = this.markerRenderer;
            PoseStack eventPoseStack = event.getPoseStack();
            float tickDelta = event.getPartialTick();
            markerRenderer.render(eventPoseStack, tickDelta);
        }
    }

    @EventHandler(priority = -9999)
    private void onRenderLines(WorldRenderEvent.Main event) {
        Setting<TracersModeChoice> mode = this.modeSetting;
        if (mode.getValue() == TracersModeChoice.LINES) {
            LineTracerRenderer lineRenderer = this.lineRenderer;
            PoseStack eventPoseStack = event.getPoseStack();
            float tickDelta = event.getPartialTick();
            lineRenderer.render(eventPoseStack, tickDelta);
        }
    }

    public Color getColor(Player player, Vec3 position) {
        Color color = this.colorSetting.getValue();
        boolean distanceColor = (this.distanceColorSetting.getValue());
        if (distanceColor) {
            double distance = (ClientAccess.evernightMinecraft.player.getEyePosition()).distanceTo(position);
            double distanceOffset = 8.0D;
            double clampedDistance = Mth.clamp(distance, 8.0D, 64.0D);
            distanceOffset = clampedDistance - 8.0D;
            float distanceFraction = 1.0F - (float) (distanceOffset / 56.0D);
            Color distanceGradient = ColorUtils.blend(Color.red, Color.green, distanceFraction);
            Color alphaColor = this.colorSetting.getValue();
            color = ColorUtils.withAlpha(distanceGradient, alphaColor.getAlpha());
        }
        relationshipColor:
        {
            SocialRegistry friendRegistry = ClientContext.socialRegistry;
            if (friendRegistry.isFriendPlayer(player)) {
                Boolean friendColorEnabled = this.friendColorSetting.getValue();
                if (friendColorEnabled) {
                    SocialRegistry capturedFriendRegistry = ClientContext.socialRegistry;
                    Color friendColor = capturedFriendRegistry.getFriendColor();
                    Color friendAlphaColor = this.colorSetting.getValue();
                    color = ColorUtils.withAlpha(friendColor, friendAlphaColor.getAlpha());
                    break relationshipColor;
                }
            }
            SocialRegistry capturedEnemyRegistry = ClientContext.socialRegistry;
            if (capturedEnemyRegistry.isEnemyPlayer(player)) {
                Boolean enemyColorEnabled = this.enemyColorSetting.getValue();
                if (enemyColorEnabled) {
                    capturedEnemyRegistry = ClientContext.socialRegistry;
                    Color enemyColor = capturedEnemyRegistry.getEnemyColor();
                    Color enemyAlphaColor = this.colorSetting.getValue();
                    color = ColorUtils.withAlpha(enemyColor, enemyAlphaColor.getAlpha());
                }
            }
        }
        if (distanceColor) {
            Setting<ColorsSchemeChoice> schemeSetting = colorsModule.schemeSetting;
            ColorsSchemeChoice configuredScheme = schemeSetting.getValue();
            color = configuredScheme.transform(color);
        }
        return color;
    }
}