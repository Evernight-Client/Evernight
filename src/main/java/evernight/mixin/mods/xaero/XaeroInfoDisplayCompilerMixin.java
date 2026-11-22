package evernight.mixin.mods.xaero;

import evernight.client.modules.misc.CoordinateHider.CoordinateHiderModule;
import evernight.client.utils.world.CoordinateCensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Redacts coordinate lines produced by Xaero minimap info displays.
 */
@Pseudo
@Mixin(targets = "xaero/hud/minimap/info/render/compile/InfoDisplayCompiler", remap = false)
public class XaeroInfoDisplayCompilerMixin {
    @ModifyVariable(method = "addWords", at = @At("HEAD"), argsOnly = true, remap = false)
    private String censorInfoWords(String text) {
        return CoordinateHiderModule.shouldHideMinimapCoords() ? CoordinateCensor.censorInfoDisplay(text) : text;
    }

    @ModifyVariable(method = "addLine(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, remap = false)
    private String censorInfoLine(String text) {
        return CoordinateHiderModule.shouldHideMinimapCoords() ? CoordinateCensor.censorInfoDisplay(text) : text;
    }
}
