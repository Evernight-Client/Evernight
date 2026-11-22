package evernight.mixin.minecraft.gui;

import evernight.client.render.color.ColorUtils;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@Mixin(GuiMessageTag.class)
public class GuiMessageTagMixin {
    @Inject(method = "indicatorColor", at = @At("HEAD"), cancellable = true)
    private void overrideClientMessageIndicatorColor(CallbackInfoReturnable<Integer> callback) {
        if ((Object) this == ChatUtils.MESSAGE_TAG) {
            Color themeColor = ColorUtils.getThemeColor();
            callback.setReturnValue(ColorUtils.pulse(themeColor, themeColor.darker(), 3000.0, 0.0).hashCode());
            callback.cancel();
        }
    }
}