package evernight.client.modules.render.PredictPath;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.mesh.LineMeshRenderer;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the predicted movement path of nearby players.
 *
 * <p>Predictions are provided by {@link ClientContext#movementPredictionManager}; this module is a
 * pure renderer on top of that data and therefore stays cheap (one sample per tick per player).
 */
public final class PredictPathModule extends Module {
    private final Setting<Integer> ticksSetting;
    private final Setting<Float> rangeSetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Color> colorSetting;
    private final Map<Integer, List<Vec3>> pathCache;

    public PredictPathModule() {
        super("PredictPath", ModuleCategory.RENDER,
                "predictpath", "pathpredict");
        this.ticksSetting = this.add(new NumberSetting<>("Ticks", 20, 1, 40));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 32.0F, 4.0F, 128.0F).withUnit("m"));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.5F, 0.5F, 5.0F));
        this.colorSetting = this.add(new ColorSetting("Color", new Color(120, 200, 255, 220)));
        this.pathCache = new HashMap<>();
    }

    @Override
    public void onDisable() {
        this.pathCache.clear();
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerTickEvent event) {
        this.pathCache.clear();
        if (this.isWorldUnavailable() || ClientContext.movementPredictionManager == null) {
            return;
        }
        int ticks = this.ticksSetting.getValue();
        float range = this.rangeSetting.getValue();
        for (AbstractClientPlayer player : ClientAccess.evernightMinecraft.level.players()) {
            if (player == ClientAccess.evernightMinecraft.player || player.isSpectator()) {
                continue;
            }
            if (player.distanceToSqr(ClientAccess.evernightMinecraft.player) > (double) range * (double) range) {
                continue;
            }
            Vec3[] points = new Vec3[ticks + 1];
            for (int tick = 0; tick <= ticks; tick++) {
                AABB predicted = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, tick);
                points[tick] = predicted.getCenter();
            }
            this.pathCache.put(player.getId(), List.of(points));
        }
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        if (this.pathCache.isEmpty()) {
            return;
        }
        float range = this.rangeSetting.getValue();
        Vec3 camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera().position();
        Color baseColor = this.colorSetting.getValue();
        LineMeshRenderer.begin(event.getPoseStack(), this.lineWidthSetting.getValue());
        boolean renderedAny = false;
        for (List<Vec3> points : this.pathCache.values()) {
            Vec3 firstPoint = points.getFirst();
            if (camera.distanceToSqr(firstPoint) > (double) range * (double) range) {
                continue;
            }
            int pointCount = points.size();
            for (int index = 1; index < pointCount; index++) {
                float alpha = 1.0F - (float) index / (float) Math.max(pointCount - 1, 1);
                Color lineColor = ColorUtils.withAlpha(baseColor, (int) (baseColor.getAlpha() * alpha));
                LineMeshRenderer.addLine(points.get(index - 1), points.get(index), lineColor);
                renderedAny = true;
            }
        }
        if (renderedAny) {
            LineMeshRenderer.draw();
        }
    }
}
