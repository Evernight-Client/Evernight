package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.render.hud.HudHorizontalAlignment;
import evernight.client.render.hud.HudVerticalAlignment;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.math.FloatBounds;
import net.minecraft.util.Mth;

import java.util.function.BiFunction;

public enum HudAnchor implements ClientAccess, NamedEntry {
    TOP_LEFT(
            "top_left",
            HudVerticalAlignment.TOP, HudHorizontalAlignment.LEFT,
            (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(0.0F, 0.0F, 10.0F, 10.0F)), TOP_RIGHT(
            "top_right",
            HudVerticalAlignment.TOP, HudHorizontalAlignment.RIGHT,
            (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(screenWidth - 10, 0.0F, 10.0F, 10.0F)),
    BOTTOM_LEFT(
            "bottom_left",
            HudVerticalAlignment.BOTTOM, HudHorizontalAlignment.LEFT,
            (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(0.0F, screenHeight - 10, 10.0F, 10.0F)),
    BOTTOM_RIGHT(
            "bottom_right",
            HudVerticalAlignment.BOTTOM, HudHorizontalAlignment.RIGHT,
            (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
                    screenWidth - 10, screenHeight - 10, 10.0F, 10.0F)), TOP_CENTER(
            "top_center",
            HudVerticalAlignment.TOP, HudHorizontalAlignment.CENTER,
            (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
                    screenWidth.intValue() / 2.0F - 10.0F, 0.0F, 10.0F, 10.0F)), NONE(
            "none",
            HudVerticalAlignment.FREE, HudHorizontalAlignment.FREE,
            (screenWidth, screenHeight) -> FloatBounds.fromPositionAndSize(
                    0.0F, 0.0F, screenWidth.intValue(), screenHeight.intValue()));
    private final String name;
    private final HudVerticalAlignment verticalAlignment;
    private final HudHorizontalAlignment horizontalAlignment;
    private final BiFunction<Integer, Integer, FloatBounds> boundsFactory;

    HudAnchor(String name, HudVerticalAlignment verticalAlignment, HudHorizontalAlignment horizontalAlignment, BiFunction<Integer, Integer, FloatBounds> boundsFactory) {
        this.name = name;
        this.verticalAlignment = verticalAlignment;
        this.horizontalAlignment = horizontalAlignment;
        this.boundsFactory = boundsFactory;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public boolean contains(double mouseX, double mouseY) {
        return this.getBounds().contains(Mth.clamp(
                mouseX, 0.0, ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth()), Mth.clamp(
                mouseY, 0.0, ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight()));
    }

    public FloatBounds getBounds() {
        return this.boundsFactory.apply(ClientAccess.evernightMinecraft.getWindow().getGuiScaledWidth(),
                ClientAccess.evernightMinecraft.getWindow().getGuiScaledHeight());
    }

    public HudHorizontalAlignment getHorizontalAlignment() {
        return this.horizontalAlignment;
    }

    public HudVerticalAlignment getVerticalAlignment() {
        return this.verticalAlignment;
    }
}