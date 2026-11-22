package evernight.client.settings.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import evernight.client.ClientContext;
import evernight.client.config.json.ColorJsonConverter;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.client.Colors.ColorsModule;
import evernight.client.render.color.ColorUtils;
import evernight.client.utils.math.MathConstants;

import java.awt.*;
import java.util.function.Predicate;
import evernight.client.settings.core.Setting;

public final class ColorSetting extends Setting<Color> {
    private static ColorsModule colorsModule;

    static {
        colorsModule = ClientContext.getModule(ColorsModule.class);
    }

    private final ColorJsonConverter colorCodec = new ColorJsonConverter();
    private boolean rainbow;
    private boolean synced;

    public ColorSetting(String name, Color defaultColor, Predicate<? super Color> visibility) {
        super(name, defaultColor, visibility);
    }

    public ColorSetting(String name, Color defaultColor) {
        super(name, defaultColor);
    }

    public static void bindModuleRegistry(ModuleRegistry registry) {
        colorsModule = registry.getModule(ColorsModule.class);
    }

    @Override
    public Color getValue() {
        return this.getColor();
    }

    @Override
    public void setValue(Color value) {
        this.setColor(value);
    }

    public boolean isRainbow() {
        if (this == colorsModule.themeColorSetting) {
            return this.rainbow;
        }
        if (!this.rainbow) {
            if (this.isSynced()) {
                ColorSetting themeSetting = (ColorSetting) colorsModule.themeColorSetting;
                return themeSetting.isRainbow();
            }
            return false;
        } else {
            return true;
        }
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public boolean isThemeColor() {
        return this == colorsModule.themeColorSetting;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonArray()) {
            ColorJsonConverter codec = this.colorCodec;
            this.setColor(codec.fromJson(json));
        } else {
            JsonObject object = json.getAsJsonObject();
            ColorJsonConverter codec = this.colorCodec;
            JsonElement rgba = object.get("rgba");
            this.setColor(codec.fromJson(rgba));
            this.setRainbow(object.get("rainbow").getAsBoolean());
            this.setSynced(object.get("sync").getAsBoolean());
        }
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        ColorJsonConverter codec = this.colorCodec;
        Color color = this.getColor();
        json.add("rgba", codec.toJson(color));
        // Persist the raw flag: isRainbow() also reports a rainbow inherited from the synced theme color,
        // which must not be written back as this setting's own state.
        json.addProperty("rainbow", this.rainbow);
        json.addProperty("sync", this.isSynced());
        return json;
    }

    public Color applyRainbow(Color color, int phaseOffset) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        float rainbowPeriodFactor = MathConstants.FULL_TURN_DEGREES;
        ColorsModule colorsSnapshot = colorsModule;
        int rainbowPeriod = (int) (rainbowPeriodFactor * colorsSnapshot.getRainbowPeriod());
        float saturation = hsb[1];
        float brightness = hsb[2];
        return ColorUtils.rainbow(rainbowPeriod, phaseOffset, saturation, brightness, color.getAlpha());
    }

    public Color getColor() {
        Color color = super.getValue();
        if (this.synced) {
            Setting<Color> themeSetting = colorsModule.themeColorSetting;
            Color themeColor = themeSetting.getValue();
            return ColorUtils.withAlpha(themeColor, color.getAlpha());
        } else if (this.rainbow) {
            return this.applyRainbow(color, 0);
        } else {
            return color;
        }
    }

    public void setColor(Color color) {
        if (this.opaqueColor) {
            super.setValue(new Color(color.hashCode(), false));
        } else {
            super.setValue(color);
        }
    }

    public boolean isSynced() {
        return (colorsModule == null || this != colorsModule.themeColorSetting) && this.synced;
    }

    public void setSynced(boolean synced) {
        if (colorsModule == null || this != colorsModule.themeColorSetting) {
            this.synced = synced;
        }
    }

    @Override
    public void parseValue(String text) {
        try {
            Color parsedColor = ColorUtils.parseHexColor(text);
            this.setColor(parsedColor);
        } catch (Throwable failure) {
        }
    }
}