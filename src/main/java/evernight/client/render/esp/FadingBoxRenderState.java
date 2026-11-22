package evernight.client.render.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.managers.player.RotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.mesh.WorldBoxRenderer;

public class FadingBoxRenderState {
    private AABB bounds;
    private long fadeStartMillis;
    private float lineWidth;

    public FadingBoxRenderState() {
        this.lineWidth = 1.0F;
    }

    public void refreshFade() {
        long currentTimeMillis = System.currentTimeMillis();
        this.fadeStartMillis = currentTimeMillis + 150L;
    }

    public AABB getBounds() {
        return this.bounds;
    }

    public void setBounds(BlockPos position) {
        this.bounds = new AABB(position);
    }

    public void setBounds(AABB bounds) {
        this.bounds = bounds;
    }

    public float getFadeFactor(float fadeDuration) {
        long elapsedMillis = Math.max(System.currentTimeMillis() - this.fadeStartMillis, 0L);
        if (fadeDuration <= 0.0F) return elapsedMillis == 0L ? 1.0F : 0.0F;
        return Mth.clamp(1.0F - elapsedMillis / fadeDuration, 0.0F, 1.0F);
    }

    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }

    public void render(
            PoseStack poseStack, Color fillColor, Color outlineColor, float fadeDuration, boolean fadeEnabled) {
        if (this.bounds != null) {
            AABB boundsSnapshot = this.bounds;
            if (RotationManager.isVisible(boundsSnapshot)) {
                float fadeFactor = this.getFadeFactor(fadeDuration);
                Color renderFill;
                if (fadeEnabled) {
                    renderFill = ColorUtils.withAlpha(fillColor, (int) (fillColor.getAlpha() * fadeFactor));
                } else {
                    renderFill = fillColor;
                }
                WorldBoxRenderer.fillBox(poseStack, this.bounds, renderFill);
                Color renderOutline;
                if (fadeEnabled) {
                    renderOutline = ColorUtils.withAlpha(outlineColor, (int) (outlineColor.getAlpha() * fadeFactor)
                    );
                } else {
                    renderOutline = outlineColor;
                }
                WorldBoxRenderer.outlineBox(poseStack, this.bounds, renderOutline, this.lineWidth);
                if (fadeFactor == 0.0F) {
                    this.bounds = null;
                }
            }
        }
    }
}