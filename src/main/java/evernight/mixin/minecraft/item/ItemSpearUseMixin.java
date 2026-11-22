package evernight.mixin.minecraft.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import evernight.client.ClientContext;
import evernight.client.modules.exploit.SpearFix.SpearFixModule;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a ViaFabricPlus "sword-spear" start and drive the client-side hold (charge) animation.
 *
 * <p>A real spear is used by holding the use key: vanilla starts the use on its {@code KineticWeapon}
 * component, which the translated sword does not have, so holding right click would do nothing at all.
 * This mixin reproduces the vanilla kinetic-weapon branch for the translated stack and reports the
 * spear use animation and duration to the renderers. The stack itself stays a sword, so packets and
 * gameplay are untouched.
 */
@Mixin(Item.class)
public class ItemSpearUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void evernight$startSpearUse(
            Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        if (spearFix == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        KineticWeapon kineticWeapon = spearFix.spearKineticWeapon(stack);
        if (kineticWeapon == null) {
            return;
        }
        player.startUsingItem(hand);
        kineticWeapon.makeSound(player);
        callback.setReturnValue(InteractionResult.CONSUME);
    }

    @ModifyReturnValue(method = "getUseAnimation", at = @At("RETURN"))
    private ItemUseAnimation evernight$spearUseAnimation(ItemUseAnimation original, ItemStack stack) {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        return spearFix != null && spearFix.appliesTo(stack) ? ItemUseAnimation.SPEAR : original;
    }

    @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
    private int evernight$spearUseDuration(int original, ItemStack stack, LivingEntity user) {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        return spearFix != null && spearFix.appliesTo(stack) ? SpearFixModule.SPEAR_USE_DURATION : original;
    }
}
