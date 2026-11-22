package evernight.mixin.minecraft.gui;

import evernight.client.ClientContext;
import evernight.client.commands.control.Panic.PanicCommand;
import evernight.client.modules.client.UI.UIModule;
import evernight.client.modules.exploit.FakeVanilla.FakeVanillaModule;
import evernight.client.render.text.Translations;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Checkbox.Builder;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JoinMultiplayerScreen.class)
public class JoinMultiplayerScreenMixin extends Screen {
    private static final FakeVanillaModule fakeVanillaModule = ClientContext.getModule(FakeVanillaModule.class);
    private static final UIModule uiModule = ClientContext.getModule(UIModule.class);

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void addVanillaToggle(CallbackInfo callback) {
        if (!PanicCommand.unloaded) {
            Builder checkboxBuilder = Checkbox.builder(Component.nullToEmpty(Translations.ui("hud.VanillaToggle")), this.font)
                    .pos(2, 6).selected(fakeVanillaModule.isToggled()).onValueChange(
                            (checkbox, isSelected) -> fakeVanillaModule.setToggled(isSelected));
            this.addRenderableWidget(checkboxBuilder.build());
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void openClickGuiOnKeyPress(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> callback) {
        if (!(this.getFocused() instanceof EditBox)) {
            if (keyEvent.key() == uiModule.getKeybind().code()) {
                callback.setReturnValue(false);
                callback.cancel();
                uiModule.enable();
                ClientContext.getClickGui().open();
            }
        }
    }
}