package evernight.client.modules.misc.InfoBlocker;

import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.ui.ChatMessageEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.StringSetSetting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Drops chat lines that contain any of the configured keywords.
 *
 * <p>A server or a spammer can push hundreds of chat packets in a burst; every line is laid out,
 * measured and appended to the chat history, which shows up as frame time spikes. The message is
 * cancelled on {@link ChatMessageEvent}'s pre stage, so the line never reaches the chat component
 * instead of being drawn first and hidden afterwards.
 *
 * <p>Matching is a case-insensitive substring test, so a keyword such as {@code pay} also catches
 * {@code PayPal}, and the list accepts any number of entries. Private messages are kept by default
 * so real trade or coordinate whispers are not swallowed together with the spam.
 */
public class InfoBlockerModule extends Module {
    public final Setting<Set<String>> wordsSetting;
    private final Setting<Boolean> ignorePrivateSetting;

    public InfoBlockerModule() {
        super("InfoBlocker", ModuleCategory.MISC);
        String wordsLabel = "Words";
        String[] defaultWords = {"lotus", "莲花"};
        this.wordsSetting = this.add(new StringSetSetting(wordsLabel, defaultWords));
        String ignorePrivateLabel = "IgnorePrivate";
        boolean ignorePrivateDefault = true;
        this.ignorePrivateSetting = this.add(new BooleanSetting(ignorePrivateLabel, ignorePrivateDefault));
    }

    @EventHandler(priority = 200)
    private void onIncomingChat(ChatMessageEvent event) {
        if (event.getStage() != EventStage.Pre) {
            return;
        }
        Component message = event.getMessage();
        if (message == null) {
            return;
        }
        Set<String> words = this.wordsSetting.getValue();
        if (words.isEmpty()) {
            return;
        }
        String text = message.getString();
        if (text.isEmpty()) {
            return;
        }
        if (this.ignorePrivateSetting.getValue() && ChatUtils.isIncomingWhisper(text)) {
            return;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (haystack.contains(word.toLowerCase(Locale.ROOT))) {
                event.cancel();
                return;
            }
        }
    }
}
