package evernight.client.access;

import evernight.client.utils.core.ClampedInterpolatedFloat;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;

public interface ChatLineAccess {
    void evernight$setAddTime(long addTime);

    long evernight$getAddTime();

    ClampedInterpolatedFloat getProgress();

    MessageSignature getSignature();

    void setSignature(MessageSignature signature);

    FormattedCharSequence getContent();

    void setContent(FormattedCharSequence content);
}