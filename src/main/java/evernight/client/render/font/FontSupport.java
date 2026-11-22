package evernight.client.render.font;

import evernight.client.ClientAccess;

import java.awt.*;
import java.io.InputStream;

/**
 * Resolves the UI font used by the client.
 *
 * <p>Evernight always renders with the bundled MiSans face; no system font lookup and no user
 * override are involved. A logical fallback is only used if the resource cannot be read.
 */
public final class FontSupport {
    public static final String BUNDLED_FONT_NAME = "MiSans";
    private static final String BUNDLED_FONT_RESOURCE = "/assets/evernight/font/misans-regular.ttf";
    private static final String FALLBACK_FONT_NAME = "SansSerif";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Object LOCK = new Object();
    private static Font bundledFont;
    private static boolean bundledFontUnavailable;

    private FontSupport() {
    }

    public static void initialize() {
        ClientAccess.executorService.submit(FontSupport::bundledFont);
    }

    /**
     * Loads (once) the MiSans text face shipped inside the mod jar.
     */
    public static Font bundledFont() {
        synchronized (LOCK) {
            if (bundledFont != null || bundledFontUnavailable) {
                return bundledFont;
            }
            try (InputStream input = FontSupport.class.getResourceAsStream(BUNDLED_FONT_RESOURCE)) {
                if (input == null) {
                    throw new IllegalStateException("Missing bundled font resource " + BUNDLED_FONT_RESOURCE);
                }
                bundledFont = Font.createFont(Font.TRUETYPE_FONT, input);
            } catch (Exception failure) {
                bundledFontUnavailable = true;
                LOGGER.warn("Could not load the bundled MiSans font", failure);
            }
            return bundledFont;
        }
    }

    /**
     * Derives the bundled face at the requested size/style. If the resource is unavailable the
     * logical {@code SansSerif} family keeps the client usable instead of failing to start.
     */
    public static Font createFont(int style, int size) {
        Font bundled = bundledFont();
        if (bundled != null) {
            return bundled.deriveFont(style, (float) size);
        }
        return new Font(FALLBACK_FONT_NAME, style, size);
    }
}
