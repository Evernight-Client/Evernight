package evernight.mixin.minecraft.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import evernight.client.ClientContext;
import evernight.client.modules.client.Fonts.FontsModule;
import evernight.client.modules.misc.BetterChat.BetterChatModule;
import evernight.client.render.font.AtlasTextRenderer;
import evernight.client.render.font.FontRenderer;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ComponentRenderUtils.class)
public class ComponentRenderUtilsMixin {
    private static final BetterChatModule betterChatModule = ClientContext.getModule(BetterChatModule.class);
    private static final FontsModule fontsModule = ClientContext.getModule(FontsModule.class);

    @ModifyExpressionValue(method = "wrapComponents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;getSplitter()Lnet/minecraft/client/StringSplitter;"))
    private static StringSplitter useChatFontSplitter(StringSplitter originalSplitter) {
        if (!fontsModule.isToggled() || !fontsModule.chatSetting.getValue()) {
            return originalSplitter;
        }
        AtlasTextRenderer textRenderer = FontRenderer.INSTANCE.getRenderer();
        return textRenderer != null ? textRenderer.getSplitter() : originalSplitter;
    }

    @ModifyVariable(method = "wrapComponents", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static FormattedText highlightOwnName(FormattedText originalText) {
        return betterChatModule.highlightNames(originalText);
    }
}