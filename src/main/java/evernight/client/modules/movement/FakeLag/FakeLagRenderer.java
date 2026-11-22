package evernight.client.modules.movement.FakeLag;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.render.color.ColorUtils;
import evernight.client.render.esp.EntityChamsRenderer;
import evernight.client.render.mesh.WorldBoxRenderer;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import evernight.client.utils.entity.SidePlayer;
import net.minecraft.world.phys.AABB;

import java.awt.*;

public final class FakeLagRenderer implements ClientAccess {
    private final FakeLagModule module;
    private final ClampedInterpolatedFloat fadeProgress;

    public FakeLagRenderer(FakeLagModule module) {
        this.fadeProgress = new ClampedInterpolatedFloat(2.0F, true);
        this.module = module;
    }

    private int fadeAlpha(int alpha) {
        return Math.max(alpha - (int) ((float) alpha * this.fadeProgress.getValue()), 0);
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        if (!this.module.renderSetting.getValue() || this.isWorldUnavailable()) {
            return;
        }
        SidePlayer snapshot = this.module.bufferedPlayerSnapshot;
        if (snapshot == null) {
            return;
        }
        this.fadeProgress.setTarget(!this.module.isToggled());
        this.fadeProgress.updateAndGet();
        Color configuredFill = this.module.fillSetting.getValue();
        Color fill = ColorUtils.withAlpha(configuredFill, this.fadeAlpha(configuredFill.getAlpha()));
        Color configuredOutline = this.module.outlineSetting.getValue();
        Color outline = ColorUtils.withAlpha(configuredOutline, this.fadeAlpha(configuredOutline.getAlpha()));
        float lineWidth = this.module.lineWidthSetting.getValue();
        PoseStack poseStack = event.getPoseStack();
        if (this.module.chamsSetting.getValue()) {
            EntityChamsRenderer.setColors(outline, fill);
            EntityChamsRenderer.setLineWidth(lineWidth);
            EntityChamsRenderer.renderEntity(poseStack, snapshot);
        } else {
            AABB bounds = snapshot.getBoundingBox();
            WorldBoxRenderer.fillBox(poseStack, bounds, fill);
            WorldBoxRenderer.outlineBox(poseStack, bounds, outline, lineWidth);
        }
    }
}