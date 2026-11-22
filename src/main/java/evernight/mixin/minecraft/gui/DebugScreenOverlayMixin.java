package evernight.mixin.minecraft.gui;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.utils.world.CoordinateCensor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Redacts coordinate lines of the vanilla F3 debug screen right before they are drawn.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
    @Inject(method = "renderLines", at = @At("HEAD"))
    private void censorCoordinateLines(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo callback) {
        if (!CoordinateHiderModule.shouldHideDebugScreen()) {
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String censored = CoordinateCensor.censorDebugLine(line);
            if (!censored.equals(line)) {
                lines.set(index, censored);
            }
        }
    }
}
