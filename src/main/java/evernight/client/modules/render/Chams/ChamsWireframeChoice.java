package evernight.client.modules.render.Chams;

import evernight.client.render.esp.ChamsColorProvider;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;

import java.awt.*;

public enum ChamsWireframeChoice implements ChamsColorProvider, NamedEntry {
    BOTH("Both") {
        @Override
        public Color[] getColors(ChamsModule chams) {
            Color[] colors = new Color[2];
            Setting<Color> lineSettingSnapshot = chams.popLineSetting;
            colors[0] = lineSettingSnapshot.getValue();
            Setting<Color> fillSettingSnapshot = chams.popFillSetting;
            colors[1] = fillSettingSnapshot.getValue();
            return colors;
        }
    },
    FILL("Fill") {
        @Override
        public Color[] getColors(ChamsModule chams) {
            Color[] colors = new Color[]{TRANSPARENT_COLOR, null};
            Setting<Color> fillSettingSnapshot = chams.popFillSetting;
            colors[1] = fillSettingSnapshot.getValue();
            return colors;
        }
    },
    LINE("Line") {
        @Override
        public Color[] getColors(ChamsModule chams) {
            Color[] colors = new Color[2];
            Setting<Color> lineSettingSnapshot = chams.popLineSetting;
            colors[0] = lineSettingSnapshot.getValue();
            colors[1] = TRANSPARENT_COLOR;
            return colors;
        }
    };
    private final String displayName;

    ChamsWireframeChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}