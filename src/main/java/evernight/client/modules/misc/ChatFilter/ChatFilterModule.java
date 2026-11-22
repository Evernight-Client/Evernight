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
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;

public class ChatFilterModule extends Module {
    public final Setting<Boolean> caseSensitiveSetting;
    private final Setting<Boolean> ignoreSelfSetting;
    private final Setting<Boolean> ignorePrivateMessagesSetting;
    private String lastSentMessage;

    public ChatFilterModule() {
        super("ChatFilter", ModuleCategory.MISC);
        caseSensitiveSetting = add(new BooleanSetting("CaseSensitive", false));
        ignoreSelfSetting = add(new BooleanSetting("IgnoreSelf", true));
        Boolean ignorePrivateMessages = Boolean.valueOf(false);
        ignorePrivateMessagesSetting = add(new BooleanSetting("IgnorePM", ignorePrivateMessages));
        lastSentMessage = null;
    }

    @Override
    public void onEnable() {
        if (!isWorldUnavailable() && ClientContext.chatFilterRegistry.isEmpty()) {
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
        if (!ClientContext.chatFilterRegistry.isEmpty()
                && ClientContext.chatFilterRegistry.matchesMessage(text)) {
            event.cancel();
        }
    }

    @EventHandler
    private void onOutgoingChat(ClientPacketListenerSendChatEvent event) {
        if (!event.isCancelled() && event.getMessage() != null && ignoreSelfSetting.getValue()) {
            lastSentMessage = event.getMessage();
        }
    }
}