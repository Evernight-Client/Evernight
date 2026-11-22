package evernight.mixin.minecraft.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.player.ItemUseEvent;
import evernight.client.modules.exploit.SpearFix.SpearFixModule;
import evernight.client.modules.movement.Sprint.SprintModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    private static final SprintModule sprintModule = ClientContext.getModule(SprintModule.class);
    @Unique
    private boolean aiStepInputActive;
    @Unique
    private boolean itemUseSlowdownCancelled;

    @Inject(
            method = "aiStep",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;input:Lnet/minecraft/client/player/ClientInput;", ordinal = 4)
    )
    private void onAiStepBeforeInput(CallbackInfo callback) {
        ItemUseEvent.Slowdown event = new ItemUseEvent.Slowdown();
        ClientAccess.eventBus.post(event);
        this.itemUseSlowdownCancelled = event.isCancelled();
        this.aiStepInputActive = true;
        if (sprintModule.isToggled() && sprintModule.shouldDeferToVanillaSprinting()) {
            SprintModule.forceSprintKeyDown = true;
        }
    }

    @Inject(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
    private void clearSprintKeyOverride(CallbackInfo callback) {
        SprintModule.forceSprintKeyDown = false;
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void finishAiStepInput(CallbackInfo callback) {
        this.aiStepInputActive = false;
    }

    @Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
    private void suppressItemUseDuringAiStep(CallbackInfoReturnable<Boolean> callback) {
        if (this.itemUseSlowdownCancelled && this.aiStepInputActive) {
            callback.setReturnValue(false);
        }
    }

    @ModifyReturnValue(method = "isSlowDueToUsingItem", at = @At("RETURN"))
    private boolean allowSprintingWhileSpearCharging(boolean original) {
        return !this.isChargingViaSpear() && original;
    }

    @ModifyReturnValue(method = "itemUseSpeedMultiplier", at = @At("RETURN"))
    private float spearChargeSpeedMultiplier(float original) {
        return this.isChargingViaSpear() ? 1.0F : original;
    }

    /**
     * Whether the use that is currently running is a ViaFabricPlus spear charge. The translated sword
     * keeps the default use effects (no sprinting, 0.2 speed), while a real spear declares
     * {@code canSprint = true} and a speed multiplier of {@code 1.0}, so the two vanilla readers of that
     * component are corrected here instead of the stack itself.
     */
    @Unique
    private boolean isChargingViaSpear() {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        return spearFix != null && spearFix.appliesTo(((LocalPlayer) (Object) this).getUseItem());
    }
}
