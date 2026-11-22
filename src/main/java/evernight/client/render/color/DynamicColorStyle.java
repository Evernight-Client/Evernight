package evernight.client.render.color;

import net.minecraft.network.chat.Style;

import java.util.function.Supplier;

public interface DynamicColorStyle {
    Style evernight$withColor(Supplier colorSupplier);
}