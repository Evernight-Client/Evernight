package evernight.mixin.minecraft.gui;

import com.llamalad7.mixinextras.sugar.Local;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.commands.control.Panic.PanicCommand;
import evernight.client.modules.client.HUD.HUDModule;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.modules.hud.Watermark.WatermarkModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
    private static final HUDModule hudModule = ClientContext.getModule(HUDModule.class);
    private static final UIModule uiModule = ClientContext.getModule(UIModule.class);
    private static final WatermarkModule watermarkModule = ClientContext.getModule(WatermarkModule.class);

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderTitleWatermark(
            GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo callback, @Local(ordinal = 1) float opacity
    ) {
        if (!PanicCommand.unloaded) {
            Minecraft client = ClientAccess.evernightMinecraft;
            int hudColor = (hudModule.fadeSetting.getValue()).getColor(hudModule, 0.0F).getRGB();
            String versionText = ChatFormatting.WHITE + watermarkModule.getVersionText();
            graphics.drawString(client.font,
                    "Evernight " + versionText,
                    2, 2, ARGB.color(opacity, hudColor));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!uiModule.getKeybind().mouse() && uiModule.getKeybind().code() == keyEvent.key()) {
            uiModule.enable();
            ClientContext.getClickGui().open();
        }
        return super.keyPressed(keyEvent);
    }
}