package evernight.client.settings.options;

import com.mojang.blaze3d.vertex.PoseStack;
import evernight.client.modules.render.Chams.ChamsModule;
import evernight.client.render.esp.ChamsColorProvider;
import evernight.client.settings.core.NamedEntry;
import net.minecraft.world.entity.Entity;

import java.awt.*;

public enum ChamsStyle implements ChamsColorProvider, NamedEntry {
    BOTH("Both"),
    FILL("Fill") {
        @Override
        public Color[] getColors(ChamsModule chams) {
            return new Color[]{TRANSPARENT_COLOR, chams.fillSetting.getValue()};
        }
    },
    LINE("Line") {
        @Override
        public Color[] getColors(ChamsModule chams) {
            Color[] colors = new Color[2];
            colors[0] = chams.outlineSetting.getValue();
            colors[1] = TRANSPARENT_COLOR;
            return colors;
        }
    },
    PLAIN("Plain") {
        @Override
        public void renderEntity(ChamsModule chams, Entity entity, PoseStack poseStack) {
        }
    },
    OFF("Off");
    private final String displayName;

    ChamsStyle(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    @Override
    public boolean isEnabled() {
        return this != OFF;
    }
}