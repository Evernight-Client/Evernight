package evernight.mixin.minecraft.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Invoker("renderTooltip")
    void drawTooltipsHook(Font font, List components, int mouseX, int mouseY, ClientTooltipPositioner positioner, @Nullable Identifier background);

    @Accessor("hoveredTextStyle")
    void evernight$hoverStyle(Style hoveredTextStyle);

    @Accessor("clickableTextStyle")
    void evernight$clickStyle(Style clickableTextStyle);

    @Accessor("mouseX")
    int evernight$mouseX();

    @Accessor("mouseY")
    int evernight$mouseY();
}