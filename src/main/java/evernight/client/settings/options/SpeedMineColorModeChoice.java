package evernight.client.settings.options;

import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;

import java.awt.*;

public enum SpeedMineColorModeChoice implements NamedEntry {
    PROGRESS("Progress") {
        @Override
        public Color[] getColors(SpeedMineModule module, float progress) {
            SpeedMineColorModeChoice customMode = CUSTOM;
            Color[] customColors = customMode.getColors(module, progress);
            Color[] colors = new Color[2];
            colors[0] = new Color(0.8F, 0.0F, 0.0F, customColors[0].getAlpha() / 255.0F);
            colors[1] = new Color(0.8F, 0.0F, 0.0F, customColors[1].getAlpha() / 255.0F);
            if (progress <= 0.8F) {
                return colors;
            }
            progress = (0.2F - (1.0F - progress)) / 0.2F;
            float unclampedRed = 0.8F - progress;
            float minimumRed = 0.0F;
            float red = Math.max(unclampedRed, minimumRed);
            colors = new Color[2];
            colors[0] = new Color(red, progress * 0.8F, 0.0F, customColors[0].getAlpha() / 255.0F);
            colors[1] = new Color(red, progress * 0.8F, 0.0F, customColors[1].getAlpha() / 255.0F);
            return colors;
        }
    },
    CUSTOM("Custom") {
        @Override
        public Color[] getColors(SpeedMineModule module, float progress) {
            Color[] colors = new Color[2];
            Setting<Color> fillSetting = module.fillSetting;
            colors[0] = fillSetting.getValue();
            Setting<Color> outlineSetting = module.outlineSetting;
            colors[1] = outlineSetting.getValue();
            return colors;
        }
    };
    private final String name;

    SpeedMineColorModeChoice(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public Color[] getColors(SpeedMineModule module, float progress) {
        return null;
    }
}