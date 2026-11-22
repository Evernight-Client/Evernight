package evernight.mixin.minecraft.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import evernight.client.access.ChatGraphicsXOffset;
import net.minecraft.client.gui.components.ChatComponent.DrawingFocusedGraphicsAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DrawingFocusedGraphicsAccess.class)
public class ChatComponentDrawingFocusedGraphicsAccessMixin implements ChatGraphicsXOffset {
    @Unique
    private int messageXOffset;

    @Override
    public void evernight$updateX(int offsetX) {
        this.messageXOffset = offsetX;
    }

    @ModifyExpressionValue(method = "handleMessage", at = @At(value = "CONSTANT", args = "intValue=0"))
    private int applyMessageXOffset(int originalX) {
        return this.messageXOffset != 0 ? this.messageXOffset : originalX;
    }
}