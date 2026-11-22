package evernight.client.render.gui;

import com.mojang.blaze3d.platform.Window;
import evernight.client.ClientAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class GuiScaleUtils implements ClientAccess {
    private static final boolean ADVANCED_UI_SCALE_LOADED = FabricLoader.getInstance().isModLoaded("advanced-ui-scale");
    private static double previousGuiScale = -1.0D;
    private static double scaleMultiplier = 1.0D;

    private GuiScaleUtils() {
    }

    public static int getBaseScaledHeight() {
        Window window = evernightMinecraft.getWindow();
        return ADVANCED_UI_SCALE_LOADED ? window.getGuiScaledHeight() : (int) Math.ceil(window.getHeight() / (double) calculateGuiScale());
    }

    public static int getScaledHeight() {
        return (int) (evernightMinecraft.getWindow().getGuiScaledHeight() / scaleMultiplier + 1.0D);
    }

    public static int getScaledWidth() {
        return (int) (evernightMinecraft.getWindow().getGuiScaledWidth() / scaleMultiplier + 1.0D);
    }

    public static void restoreGuiScale() {
        if (previousGuiScale < 0.0D) {
            return;
        }
        evernightMinecraft.getWindow().setGuiScale((int) previousGuiScale);
        previousGuiScale = -1.0D;
        scaleMultiplier = 1.0D;
    }

    public static boolean isAdvancedUiScaleLoaded() {
        return ADVANCED_UI_SCALE_LOADED;
    }

    public static double getScaleMultiplier() {
        return scaleMultiplier;
    }

    public static void applyDefaultGuiScale() {
        applyGuiScale(1.0F);
    }

    public static int calculateGuiScale() {
        Minecraft minecraft = evernightMinecraft;
        return minecraft.getWindow().calculateScale(2, minecraft.isEnforceUnicode());
    }

    public static void applyGuiScale(float scale) {
        if (ADVANCED_UI_SCALE_LOADED) {
            return;
        }
        Window window = evernightMinecraft.getWindow();
        if (previousGuiScale < 0.0D) {
            previousGuiScale = window.getGuiScale();
        }
        scaleMultiplier = scale;
        window.setGuiScale(calculateGuiScale());
    }

    public static double calculateScaleMultiplier(float scale) {
        return ADVANCED_UI_SCALE_LOADED ? 1.0D : (double) calculateGuiScale() / evernightMinecraft.getWindow().getGuiScale() * scale;
    }
}