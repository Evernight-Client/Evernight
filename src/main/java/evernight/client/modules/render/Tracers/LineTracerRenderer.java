package evernight.client.modules.render.Tracers;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.ClientAccess;
import evernight.client.render.mesh.HudTracerRenderer;
import evernight.client.render.mesh.LineMeshRenderer;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.TracersHitboxChoice;
import evernight.client.utils.math.Vec3Utils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Iterator;

public final class LineTracerRenderer extends TracerRenderer {
    public LineTracerRenderer(TracersModule module) {
        super(module);
    }

    @Override
    public void render(PoseStack poseStack, float unusedTickDelta) {
        LineMeshRenderer.begin(poseStack, this.module.lineWidthSetting.getValue());
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        Iterator players = level.players().iterator();
        while (players.hasNext()) {
            AbstractClientPlayer player = (AbstractClientPlayer) players.next();
            if (!this.module.shouldSkip(player)) {
                TracersModule geometryModule = this.module;
                TracerEndpoints endpoints = geometryModule.getEndpoints(player);
                Setting<TracersHitboxChoice> hitboxSetting = this.module.hitboxSetting;
                TracersHitboxChoice hitbox = hitboxSetting.getValue();
                float configuredHitboxFraction = hitbox.getHeightFraction();
                Vec3 start = endpoints.start();
                Vec3 capturedEnd = endpoints.end();
                Vec3 hitboxPosition = Vec3Utils.lerp(configuredHitboxFraction, start, capturedEnd);
                Color color = this.module.getColor(player, hitboxPosition);
                HudTracerRenderer.add(hitboxPosition, color, this.module.lineWidthSetting.getValue());
                Setting<Boolean> stemSetting = this.module.stemSetting;
                Boolean drawStem = stemSetting.getValue();
                if (drawStem) {
                    Vec3 stemStart = endpoints.start();
                    LineMeshRenderer.addLine(stemStart, endpoints.end(), color);
                }
            }
        }
        LineMeshRenderer.draw();
    }
}