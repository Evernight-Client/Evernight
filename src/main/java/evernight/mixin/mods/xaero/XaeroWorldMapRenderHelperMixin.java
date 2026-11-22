package evernight.mixin.mods.xaero;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.utils.world.CoordinateCensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Redacts coordinate text drawn by Xaero's world map.
 */
@Pseudo
@Mixin(targets = "xaero/map/graphics/MapRenderHelper", remap = false)
public class XaeroWorldMapRenderHelperMixin {
    @ModifyVariable(
            method = "drawCenteredStringWithBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIFFFF)V",
            at = @At("HEAD"),
            argsOnly = true)
    private static String censorCenteredMapText(String text) {
        return CoordinateHiderModule.shouldHideWorldMapCoords() ? CoordinateCensor.censorMapText(text) : text;
    }

    @ModifyVariable(
            method = "drawStringWithBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIFFFF)V",
            at = @At("HEAD"),
            argsOnly = true)
    private static String censorMapText(String text) {
        return CoordinateHiderModule.shouldHideWorldMapCoords() ? CoordinateCensor.censorMapText(text) : text;
    }
}
