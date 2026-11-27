package evernight.client.render.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.HudRenderEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.render.gui.GuiDrawUtils;
import evernight.client.render.util.RenderUtils;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects tracers during the world render pass and draws them as screen-space lines from the
 * crosshair in the 2D HUD pass.
 *
 * <p>The old world-space tracer projected a world point to the screen and then unprojected it back
 * to the near plane. Tiny camera or partial-tick differences made that endpoint jitter while
 * moving. Drawing in the HUD layer keeps the line anchored to the crosshair and stable.
 */
public final class HudTracerRenderer implements ClientAccess {
    private static final int MAX_TRACERS = 512;
    private static final List<Tracer> tracers = new ArrayList<>();
    private static final Matrix4f projection = new Matrix4f();
    private static final Vector4f clip = new Vector4f();

    static {
        ClientAccess.eventBus.subscribeStatic(HudTracerRenderer.class);
    }

    private HudTracerRenderer() {
    }

    public static void add(Vec3 position, Color color) {
        add(position, color, 1.0F);
    }

    public static void add(Vec3 position, Color color, float width) {
        add(position, color.hashCode(), width);
    }

    public static void add(Vec3 position, int color) {
        add(position, color, 1.0F);
    }

    public static void add(Vec3 position, int color, float width) {
        if (tracers.size() < MAX_TRACERS) {
            tracers.add(new Tracer(position, color, width));
        }
    }

    /** Drops the previous frame's queue before the world render starts collecting a new one. */
    @EventHandler
    private static void onWorldRenderStart(WorldRenderEvent.BeforeDepthClear event) {
        tracers.clear();
    }

    @EventHandler
    private static void onHudRender(HudRenderEvent.Before event) {
        if (tracers.isEmpty() || ClientAccess.evernightMinecraft.player == null
                || ClientAccess.evernightMinecraft.level == null) {
            return;
        }
        Vec3 camera = ClientAccess.evernightMinecraft.gameRenderer.getMainCamera().position();
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        RenderUtils.applyCameraRotation(view);
        RenderUtils.WORLD_PROJECTION_MATRIX.mul(view, projection);
        int screenWidth = ClientAccess.evernightMinecraft.getWindow().getScreenWidth();
        int screenHeight = ClientAccess.evernightMinecraft.getWindow().getScreenHeight();
        float centerX = screenWidth * 0.5F;
        float centerY = screenHeight * 0.5F;
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            float scale = 1.0F / ClientAccess.evernightMinecraft.getWindow().getGuiScale();
            poses.scale(scale, scale, scale);
            boolean drewAny = false;
            for (Tracer tracer : tracers) {
                Vector4f projected = project(camera, tracer.position());
                if (projected == null) {
                    continue;
                }
                float ndcX = projected.x / projected.w;
                float ndcY = projected.y / projected.w;
                float extent = Math.max(Math.abs(ndcX), Math.abs(ndcY));
                if (extent > 1.0F) {
                    ndcX /= extent;
                    ndcY /= extent;
                }
                float targetX = (ndcX + 1.0F) * screenWidth * 0.5F;
                float targetY = (1.0F - ndcY) * screenHeight * 0.5F;
                VertexGeometry.appendLine(GuiDrawUtils.getLineVertices(), poses.last(), centerX, centerY, 0.0F,
                        targetX, targetY, 0.0F, tracer.width(), tracer.color());
                drewAny = true;
            }
            if (drewAny) {
                GuiDrawUtils.flush();
            }
        } finally {
            poses.popPose();
            tracers.clear();
        }
    }

    private static Vector4f project(Vec3 camera, Vec3 position) {
        clip.set((float) (position.x - camera.x), (float) (position.y - camera.y),
                (float) (position.z - camera.z), 1.0F);
        projection.transform(clip);
        return clip.w <= 1.0E-4F ? null : clip;
    }

    private record Tracer(Vec3 position, int color, float width) {
    }
}
