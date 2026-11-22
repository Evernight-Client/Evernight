package evernight.mixin.minecraft.gui;

import evernight.client.access.ChatLineAccess;
import evernight.client.modules.misc.BetterChat.BetterChatModule;
import evernight.client.utils.core.ClampedInterpolatedFloat;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Line.class)
public class GuiMessageLineMixin implements ChatLineAccess {
    @Unique
    private ClampedInterpolatedFloat progress = null;
    @Unique
    private MessageSignature signature;
    @Mutable
    @Final
    @Shadow
    private FormattedCharSequence content;
    @Unique
    private long addTimeMillis;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeChatLineAnimation(
            int addedAtTick, FormattedCharSequence text, GuiMessageTag tag, boolean endOfEntry, CallbackInfo callback
    ) {
        this.getProgress().setValue(false);
        this.addTimeMillis = System.currentTimeMillis();
    }

    @Override
    public MessageSignature getSignature() {
        return this.signature;
    }

    @Override
    public void setSignature(MessageSignature newSignature) {
        this.signature = newSignature;
    }

    @Override
    public FormattedCharSequence getContent() {
        return this.content;
    }

    @Override
    public void setContent(FormattedCharSequence newContent) {
        this.content = newContent;
    }

    @Override
    public ClampedInterpolatedFloat getProgress() {
        if (this.progress == null) {
            this.progress = new ClampedInterpolatedFloat(() -> BetterChatModule.getInstance().speedSetting
                    .getValue() * 2.0F, true);
        }
        return this.progress;
    }

    @Override
    public long evernight$getAddTime() {
        return this.addTimeMillis;
    }

    @Override
    public void evernight$setAddTime(long addedAtMillis) {
        this.addTimeMillis = addedAtMillis;
    }
}