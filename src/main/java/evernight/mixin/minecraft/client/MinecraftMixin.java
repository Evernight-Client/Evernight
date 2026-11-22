package evernight.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import evernight.addon.AddonManager;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.events.ui.ScreenChangeEvent;
import evernight.client.gui.screens.core.ConfirmGameCloseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    private static boolean shutdownSaveStarted = false;
    @Shadow
    public int missTime;
    @Shadow
    @Nullable
    public Screen screen;
    @Unique
    private Screen previousScreen;

    @Inject(method = "startAttack", at = @At("HEAD"))
    public void resetAttackMissTime(CallbackInfoReturnable<Boolean> callback) {
        if (MixinModuleRefs.noHitDelay.isToggled() || MixinModuleRefs.autoClicker.isToggled()) {
            this.missTime = 0;
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    public void saveConfigurationOnDestroy(CallbackInfo callback) {
        if (!shutdownSaveStarted && ClientContext.configurationManager != null) {
            shutdownSaveStarted = true;
            MixinModuleRefs.ui.disable();
            ClientContext.configurationManager.save();
            AddonManager.getInstance().shutdown();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;close()V"))
    public void postDisconnectEvent(
            Screen screen, boolean ignoredDisconnectFlag1, boolean ignoredDisconnectFlag2, CallbackInfo callback) {
        MinecraftDisconnectEvent event = new MinecraftDisconnectEvent();
        ClientAccess.eventBus.post(event);
    }

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    public boolean bypassUsingItemForContinueAttack(boolean originalUsingItem) {
        return !MixinModuleRefs.multiTask.isToggled() && originalUsingItem;
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"), require = 0)
    public boolean bypassDestroyingForStartUseItem(boolean originalDestroying) {
        return !MixinModuleRefs.multiTask.isToggled() && originalDestroying;
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void postBeforeScreenChange(Screen newScreen, CallbackInfo callback) {
        this.previousScreen = this.screen;
        ScreenChangeEvent.Before event = new ScreenChangeEvent.Before(this.previousScreen, newScreen);
        ClientAccess.eventBus.post(event);
        if (event.isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init(II)V", shift = Shift.AFTER), cancellable = true)
    private void postAfterScreenInit(Screen newScreen, CallbackInfo callback) {
        ScreenChangeEvent.AfterInit event = new ScreenChangeEvent.AfterInit(this.previousScreen, newScreen);
        ClientAccess.eventBus.post(event);
        if (event.isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void postMinecraftTick(CallbackInfo callback) {
        ClientAccess.eventBus.post(MinecraftTickEvent.INSTANCE);
    }

    @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean bypassUsingItemForAutoEat(boolean originalUsingItem) {
        return (!MixinModuleRefs.autoEat.isEating() || !MixinModuleRefs.autoEat.isToggled()) && originalUsingItem;
    }

    @WrapWithCondition(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;stop()V"))
    private boolean confirmGameClose(Minecraft client) {
        if (MixinModuleRefs.antiQuit.isToggled() && MixinModuleRefs.antiQuit.gameCloseSetting.getValue()) {
            if (!(client.screen instanceof ConfirmGameCloseScreen)) {
                client.setScreen(new ConfirmGameCloseScreen());
            }
            return false;
        } else {
            return true;
        }
    }

    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V", shift = Shift.AFTER))
    private void postMinecraftRunTick(boolean renderLevel, CallbackInfo callback, @Local int ticksToRun) {
        ClientAccess.eventBus.post(new MinecraftRunTickEvent(ticksToRun >= 1));
    }

    @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean bypassUsingItemForArrows(boolean originalUsingItem) {
        return !MixinModuleRefs.arrows.shouldBypassUsingItemCheck() && originalUsingItem;
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void completeCrystalCalculationBeforeUse(CallbackInfo callback) {
        MixinModuleRefs.autoCrystal.completeCalculation();
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
    private boolean bypassHandsBusyForStartUseItem(boolean originalHandsBusy) {
        return !MixinModuleRefs.multiTask.isToggled() && originalHandsBusy;
    }

    @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
    private boolean bypassHandsBusyForStartAttack(boolean originalHandsBusy) {
        return !MixinModuleRefs.multiTask.isToggled() && originalHandsBusy;
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void suppressEntityGlow(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (MixinModuleRefs.noRender.isToggled() && MixinModuleRefs.noRender.glowSetting.getValue()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    public void closeDiscordClient(CallbackInfo callback) {
        MixinModuleRefs.discord.closeClient();
    }
}
