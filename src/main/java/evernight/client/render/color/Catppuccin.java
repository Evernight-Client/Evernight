package evernight.client.render.color;

import java.awt.*;

/**
 * The Catppuccin Mocha palette, used as the reference for the client's UI colors.
 *
 * <p>Values are taken from the official palette:
 * <a href="https://catppuccin.com/palette/">https://catppuccin.com/palette/</a>.
 * Mocha is the darkest and most popular flavor, which keeps the translucent
 * ClickGUI panels readable against in-game scenes.
 */
public final class Catppuccin {
    // Accents
    public static final Color ROSEWATER = new Color(0xF5E0DC);
    public static final Color FLAMINGO = new Color(0xF2CDCD);
    public static final Color PINK = new Color(0xF5C2E7);
    public static final Color MAUVE = new Color(0xCBA6F7);
    public static final Color RED = new Color(0xF38BA8);
    public static final Color MAROON = new Color(0xEBA0AC);
    public static final Color PEACH = new Color(0xFAB387);
    public static final Color YELLOW = new Color(0xF9E2AF);
    public static final Color GREEN = new Color(0xA6E3A1);
    public static final Color TEAL = new Color(0x94E2D5);
    public static final Color SKY = new Color(0x89DCEB);
    public static final Color SAPPHIRE = new Color(0x74C7EC);
    public static final Color BLUE = new Color(0x89B4FA);
    public static final Color LAVENDER = new Color(0xB4BEFE);
    // Text
    public static final Color TEXT = new Color(0xCDD6F4);
    public static final Color SUBTEXT1 = new Color(0xBAC2DE);
    public static final Color SUBTEXT0 = new Color(0xA6ADC8);
    // Overlays
    public static final Color OVERLAY2 = new Color(0x9399B2);
    public static final Color OVERLAY1 = new Color(0x7F849C);
    public static final Color OVERLAY0 = new Color(0x6C7086);
    // Surfaces
    public static final Color SURFACE2 = new Color(0x585B70);
    public static final Color SURFACE1 = new Color(0x45475A);
    public static final Color SURFACE0 = new Color(0x313244);
    public static final Color BASE = new Color(0x1E1E2E);
    public static final Color MANTLE = new Color(0x181825);
    public static final Color CRUST = new Color(0x11111B);

    private Catppuccin() {
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }
}
