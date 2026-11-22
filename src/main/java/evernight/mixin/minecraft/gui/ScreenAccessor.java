package evernight.mixin.minecraft.gui;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("renderables")
    List getDrawables();

    @Accessor("renderables")
    void setDrawables(List renderables);

    @Invoker("addRenderableWidget")
    GuiEventListener addDrawableChildHook(GuiEventListener child);
}