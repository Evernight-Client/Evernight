package evernight.client.modules.misc.AntiSpam;

import evernight.client.ClientAccess;
import evernight.client.access.VisibleChatLinesAccess;
import evernight.client.events.ui.ChatMessageEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.GuiMessage.Line;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class AntiSpamModule extends Module {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://)?([\\w\\-])+\\.{1}([a-zA-Z]{2,63})([/\\w-]*)*/?\\??([^#\\n\\r]*)?#?([^\\n\\r]*)");
    private static final Pattern INVITE_PATTERN = Pattern.compile("gg/([a-zA-Z0-9]*)");
    public final Setting<Boolean> indicatorSetting;
    private final Map<String, MessageStack> messageStacks = new HashMap<>();
    private final Setting<Boolean> linksSetting;
    private String pendingMessage;

    public AntiSpamModule() {
        super("AntiSpam", ModuleCategory.MISC);
        indicatorSetting = add(new BooleanSetting("Indicator", true));
        linksSetting = add(new BooleanSetting("Links", false));
        setDrawn(false);
    }

    @Override
    public void onToggle() {
        messageStacks.clear();
        pendingMessage = null;
    }

    @EventHandler
    private void onDisconnect(MinecraftDisconnectEvent event) {
        messageStacks.clear();
        pendingMessage = null;
    }

    @EventHandler(priority = 200)
    private void onChatLine(ChatMessageEvent event) {
        if (event.getStage() == EventStage.Pre) {
            if (event.getSignature() != null && event.getSignature().asByteBuffer().getInt() < 0) {
                return;
            }
            if (event.getTag() == ChatUtils.MESSAGE_TAG) {
                return;
            }
            pendingMessage = event.getMessage().getString().toLowerCase(Locale.ROOT);
            if (linksSetting.getValue() && containsLink(pendingMessage)) {
                event.cancel();
                return;
            }
            if (indicatorSetting.getValue()) {
                messageStacks.computeIfPresent(pendingMessage, (message, stack) -> {
                    ((VisibleChatLinesAccess) ClientAccess.evernightMinecraft.gui.getChat()).getVisible().remove(stack.line());
                    return stack;
                });
                messageStacks.putIfAbsent(pendingMessage, new MessageStack(1L, event.getLine()));
                long count = messageStacks.get(pendingMessage).count();
                if (count > 1L) {
                    event.setMessage(Component.empty().append(event.getMessage())
                            .append(Component.literal(" [x" + count + "]")));
                }
            }
        } else if (pendingMessage != null && indicatorSetting.getValue()) {
            messageStacks.computeIfPresent(pendingMessage, (message, stack) -> new MessageStack(stack.count() + 1L,
                    event.getLine()));
        }
    }

    private boolean containsLink(String message) {
        return URL_PATTERN.matcher(message).find() || INVITE_PATTERN.matcher(message).find();
    }

    private record MessageStack(long count, Line line) {
    }
}