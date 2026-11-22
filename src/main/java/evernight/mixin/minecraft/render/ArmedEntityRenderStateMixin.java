package evernight.mixin.minecraft.render;

import evernight.client.ClientContext;
import evernight.client.modules.exploit.SpearFix.SpearFixModule;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the third person renderer the real spear stack for ViaFabricPlus "sword-spears".
 *
 * <p>The render state keeps a copy of the held stack for the arm pose and item animation, and
 * {@code HumanoidModel} / {@code SpearAnimations} read the kinetic weapon component from it. Replacing
 * the copy with the material-matched spear keeps the hold (charge) animation usable without touching
 * the stack the player actually holds.
 */
@Mixin(ArmedEntityRenderState.class)
public class ArmedEntityRenderStateMixin {
    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void evernight$storeSpearStacks(
            LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver itemModelResolver, float tickProgress,
            CallbackInfo callback) {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        if (spearFix == null) {
            return;
        }
        state.rightHandItemStack = spearFix.holdAnimationStack(state.rightHandItemStack);
        state.leftHandItemStack = spearFix.holdAnimationStack(state.leftHandItemStack);
    }
}
