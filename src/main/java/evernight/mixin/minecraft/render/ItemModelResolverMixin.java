package evernight.mixin.minecraft.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import evernight.client.ClientContext;
import evernight.client.modules.exploit.SpearFix.SpearFixModule;
import evernight.client.utils.inventory.ItemUtils;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Renders ViaFabricPlus "sword-spears" with their real spear item models.
 *
 * <p>When the connection protocol is downgraded, the server-side spear item is translated into a
 * sword carrying Via's original-item marker. Gameplay code must keep treating that stack as a sword
 * for packet purposes, so the substitution happens only inside the item model resolver and only for
 * the duration of the render call.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
    /**
     * {@code swap_animation_scale} of the vanilla spear item models ({@code assets/minecraft/items/*_spear.json}).
     */
    private static final float SPEAR_SWAP_ANIMATION_SCALE = 1.95F;

    private static ItemStack evernight$replaceViaSpear(ItemStack stack) {
        return evernight$shouldReplace(stack) ? ItemUtils.getSpearRenderStack(stack) : stack;
    }

    private static boolean evernight$shouldReplace(ItemStack stack) {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        return spearFix != null && spearFix.appliesTo(stack);
    }

    @ModifyVariable(method = "updateForLiving", at = @At("HEAD"), argsOnly = true, index = 2)
    private ItemStack evernight$replaceLivingSpear(ItemStack stack) {
        return evernight$replaceViaSpear(stack);
    }

    @ModifyVariable(method = "updateForNonLiving", at = @At("HEAD"), argsOnly = true, index = 2)
    private ItemStack evernight$replaceNonLivingSpear(ItemStack stack) {
        return evernight$replaceViaSpear(stack);
    }

    @ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true, index = 2)
    private ItemStack evernight$replaceTopItemSpear(ItemStack stack) {
        return evernight$replaceViaSpear(stack);
    }

    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true, index = 2)
    private ItemStack evernight$replaceLayerSpear(ItemStack stack) {
        return evernight$replaceViaSpear(stack);
    }

    @ModifyReturnValue(method = "swapAnimationScale", at = @At("RETURN"))
    private float evernight$spearSwapAnimationScale(float original, ItemStack stack) {
        return evernight$shouldReplace(stack) ? SPEAR_SWAP_ANIMATION_SCALE : original;
    }
}
