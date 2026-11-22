package evernight.client.access;

import evernight.client.ClientAccess;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TooltipComponentCapture implements ClientAccess {
    private static boolean captureEnabled;
    private static List capturedComponents;

    static {
        captureEnabled = false;
    }

    private TooltipComponentCapture() {
        throw new AssertionError();
    }

    public static List captureComponents(GuiGraphics graphics, List text, Optional tooltipComponent) {
        captureEnabled = true;
        Font font = ClientAccess.evernightMinecraft.font;
        graphics.setTooltipForNextFrame(font, text, tooltipComponent, 0, 0);
        captureEnabled = false;
        return capturedComponents == null ? new ArrayList() : new ArrayList(capturedComponents);
    }

    public static boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public static void setCapturedComponents(List components) {
        capturedComponents = components;
    }
}