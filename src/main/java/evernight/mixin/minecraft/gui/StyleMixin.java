package evernight.mixin.minecraft.gui;

import evernight.client.render.color.DynamicColorStyle;
import evernight.client.render.color.TextColorSupplierAccess;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Supplier;

@Mixin(Style.class)
public class StyleMixin implements DynamicColorStyle {
    @Unique
    @Override
    public Style evernight$withColor(Supplier supplier) {
        Style style = (Style) (Object) this;
        if (style.getColor() != null) {
            ((TextColorSupplierAccess) (Object) style.getColor()).setSupplier(supplier);
        }
        return style;
    }
}