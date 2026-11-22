package evernight.mixin.minecraft.gui;

import com.llamalad7.mixinextras.sugar.Local;
import evernight.client.ClientContext;
import evernight.client.gui.screens.core.DisconnectConfirmationScreen;
import evernight.client.modules.exploit.IllegalDisconnect.IllegalDisconnectModule;
import evernight.client.modules.misc.AntiQuit.AntiQuitModule;
import evernight.client.modules.misc.ZenithProxy.ZenithProxyModule;
import evernight.client.render.text.Translations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    private static final AntiQuitModule antiQuitModule = ClientContext.getModule(AntiQuitModule.class);
    private static final IllegalDisconnectModule illegalDisconnectModule = ClientContext.getModule(IllegalDisconnectModule.class);
    @Unique
    private static boolean disconnectConfirmed;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"
    ))
    private void addZenithProxyButtons(CallbackInfo callback, @Local GridLayout.RowHelper rowHelper) {
        ZenithProxyModule zenithProxy = ClientContext.getModule(ZenithProxyModule.class);
        if (zenithProxy == null || !zenithProxy.shouldShowPauseMenuButtons()) {
            return;
        }
        rowHelper.addChild(Button.builder(
                Component.literal(Translations.ui("modules.ZenithProxy.DisconnectProxy")),
                button -> {
                    button.active = false;
                    zenithProxy.closeProxy();
                }).width(98).build());
        rowHelper.addChild(Button.builder(
                Component.literal(Translations.ui("modules.ZenithProxy.SwapSpectator")),
                button -> {
                    button.active = false;
                    zenithProxy.swapSpectator();
                }).width(98).build());
    }

    // Mojang leaves the disconnect lambda unnamed.
    @Inject(method = "method_72129()V", at = @At("HEAD"), cancellable = true)
    private void confirmOrInterceptDisconnect(CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (antiQuitModule.isToggled() && antiQuitModule.disconnectSetting.getValue() && !disconnectConfirmed) {
            client.setScreen(new DisconnectConfirmationScreen(() -> {
                disconnectConfirmed = true;
                client.getReportingContext().draftReportHandled(client, this, () -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
            }));
            callback.cancel();
        } else {
            if (illegalDisconnectModule.isToggled() && client.player != null && !client.isLocalServer()) {
                illegalDisconnectModule.requestDisconnect();
                callback.cancel();
            }
        }
    }
}