package evernight.mixin.minecraft.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import evernight.client.ClientContext;
import evernight.client.modules.exploit.SpearFix.SpearFixModule;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives ViaFabricPlus "sword-spears" the stabbing swing animation of a real spear.
 *
 * <p>The swing animation (type and duration) is read from the held stack's {@code SwingAnimation}
 * component by the first person renderer, the humanoid render state and the swing duration. A translated
 * spear is still a sword, so vanilla would whack instead of stab; this returns the real spear's animation
 * for that stack, which keeps first and third person in sync with the spear item model.
 */
@Mixin(ItemStack.class)
public class ItemStackSwingAnimationMixin {
    @ModifyReturnValue(method = "getSwingAnimation", at = @At("RETURN"))
    private SwingAnimation evernight$spearSwingAnimation(SwingAnimation original) {
        SpearFixModule spearFix = ClientContext.getModule(SpearFixModule.class);
        if (spearFix == null) {
            return original;
        }
        SwingAnimation spearAnimation = spearFix.spearSwingAnimation((ItemStack) (Object) this);
        return spearAnimation == null ? original : spearAnimation;
    }
}
