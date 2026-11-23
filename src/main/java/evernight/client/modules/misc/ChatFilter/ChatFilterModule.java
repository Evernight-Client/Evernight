package evernight.client.modules.misc.ChatFilter;

import evernight.client.ClientContext;
import evernight.client.events.ui.ChatMessageEvent;
import evernight.client.events.network.ClientPacketListenerSendChatEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.StringSetSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Drops chat lines before they reach the chat component, using two independent sources:
 * the {@code ;chatfilter} filters (an id/filter registry, see {@link ClientContext#chatFilterRegistry})
 * and the keyword list that is edited through the ClickGUI.
 *
 * <p>Keyword matching is a case-insensitive substring test, so an entry such as {@code pay} also
 * catches {@code PayPal}; the list accepts any number of entries and needs no command to maintain.
 */
public class ChatFilterModule extends Module {
    public final Setting<Boolean> caseSensitiveSetting;
    private final Setting<Boolean> ignoreSelfSetting;
    private final Setting<Boolean> ignorePrivateMessagesSetting;
    private final Setting<Set<String>> wordsSetting;
    private String lastSentMessage;

    public ChatFilterModule() {
        super("ChatFilter", ModuleCategory.MISC);
        caseSensitiveSetting = add(new BooleanSetting("CaseSensitive", false));
        ignoreSelfSetting = add(new BooleanSetting("IgnoreSelf", true));
        ignorePrivateMessagesSetting = add(new BooleanSetting("IgnorePM", false));
        wordsSetting = add(new StringSetSetting("Words", "lotus"));
        lastSentMessage = null;
    }

    @Override
    public void onEnable() {
        if (!isWorldUnavailable() && ClientContext.chatFilterRegistry.isEmpty()
                && this.wordsSetting.getValue().isEmpty()) {
            String notice = Translations.format("modules.ChatFilter.NoFilters",
                    CommandRegistry.getPrefix());
            ChatUtils.printChat(Component.nullToEmpty(notice), ChatUtils.moduleSignature(this),
                    ChatUtils.MessagePriority.LOW);
        }
        lastSentMessage = null;
    }

    @EventHandler(priority = 200)
    private void onIncomingChat(ChatMessageEvent event) {
        if (event.getStage() != EventStage.Pre || event.getMessage() == null) {
            return;
        }
        GuiMessageTag tag = event.getTag();
        if (tag != null && tag != GuiMessageTag.system() && tag != GuiMessageTag.systemSinglePlayer() && tag != GuiMessageTag.chatNotSecure()) {
            return;
        }
        String text = event.getMessage().getString();
        if (text == null) {
            return;
        }
        if (ignoreSelfSetting.getValue() && lastSentMessage != null && text.contains(lastSentMessage)) {
            lastSentMessage = null;
            return;
        }
        if (ignorePrivateMessagesSetting.getValue() && ChatUtils.isIncomingWhisper(text)) {
            return;
        }
        if (this.matchesWord(text) || (!ClientContext.chatFilterRegistry.isEmpty()
                && ClientContext.chatFilterRegistry.matchesMessage(text))) {
            event.cancel();
        }
    }

    /**
     * Case-insensitive substring test against the headline keyword list. Entries are normalised to
     * lower case when added, but config files may contain mixed case, so both sides are lowered.
     */
    private boolean matchesWord(String text) {
        Set<String> words = this.wordsSetting.getValue();
        if (words.isEmpty()) {
            return false;
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (haystack.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onOutgoingChat(ClientPacketListenerSendChatEvent event) {
        if (!event.isCancelled() && event.getMessage() != null && ignoreSelfSetting.getValue()) {
            lastSentMessage = event.getMessage();
        }
    }
}
