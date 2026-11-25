package evernight.client.modules.player.PearlBot;

import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.ui.ChatMessageEvent;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.StringSetSetting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.chat.PrivateMessageParser;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Triggers the local pearl chamber on demand from a private message.
 *
 * <p>A friend whispers a trigger word (拉 by default); the module looks for a nearby sign carrying the
 * sender's name, walks to the button or trapdoor placed one or two blocks below that sign, clicks it
 * and walks back to where it started. Buttons are pressed once; a trapdoor is opened and closed again
 * one tick later so the chamber stays armed for the next pearl.
 *
 * <p>Walking is delegated to Baritone, so the module refuses to run when it is not installed. Every
 * reply is shown in the local chat and, when a reply command is configured, whispered back to the
 * requester with {@code /msg <player> <message>}.
 */
public final class PearlBotModule extends Module {
    private static final String TRANSLATION_PREFIX = "modules.PearlBot.";

    private final Setting<Set<String>> keywordsSetting;
    private final Setting<Float> scanRangeSetting;
    private final Setting<String> replyCommandSetting;
    private final Setting<String> foundMessageSetting;
    private final Setting<String> notFoundMessageSetting;
    private final Setting<String> comingMessageSetting;
    private final Setting<String> doneMessageSetting;
    private final Setting<Integer> trapdoorDelaySetting;
    private final Setting<Boolean> returnSetting;
    private final PearlBotController controller;

    public PearlBotModule() {
        super("PearlBot", ModuleCategory.AUTO, "pearlbot", "pearl");
        this.keywordsSetting = this.add(new StringSetSetting("Keywords", "拉"));
        this.scanRangeSetting = this.add(new NumberSetting<>("ScanRange", 16.0F, 4.0F, 64.0F).withUnit("m"));
        this.replyCommandSetting = this.add(new StringSetting("ReplyCommand", "/msg {player} {message}"));
        this.foundMessageSetting = this.add(new StringSetting("FoundMessage", "找到了"));
        this.notFoundMessageSetting = this.add(new StringSetting("NotFoundMessage", "没找到你珍珠啊"));
        this.comingMessageSetting = this.add(new StringSetting("ComingMessage", "正在前往"));
        this.doneMessageSetting = this.add(new StringSetting("DoneMessage", "已完成"));
        this.trapdoorDelaySetting = this.add(new NumberSetting<>("TrapdoorDelay", 1, 1, 20));
        this.returnSetting = this.add(new BooleanSetting("Return", true));
        this.returnSetting.hideWithoutBaritone();
        this.controller = new PearlBotController(this);
    }

    @Override
    public void onEnable() {
        this.controller.reset();
        if (ClientContext.baritoneBridge == null || !ClientContext.baritoneBridge.isAvailable()) {
            this.notifyError("NoBaritone");
        }
    }

    @Override
    public void onDisable() {
        this.controller.shutdown();
    }

    @Override
    public String getInfo() {
        return this.isToggled() ? this.controller.getPhaseName() : null;
    }

    @EventHandler(priority = 100)
    private void onChatMessage(ChatMessageEvent event) {
        if (event.isCancelled() || event.getStage() != EventStage.Pre) {
            return;
        }
        PrivateMessageParser.PrivateMessage message = PrivateMessageParser.parse(event.getOriginalText());
        if (message == null || message.isBlank() || !this.matchesKeyword(message.content())) {
            return;
        }
        this.controller.request(message.sender());
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerTickEvent event) {
        this.controller.tick();
    }

    @EventHandler
    private void onLocalPlayerSendPosition(LocalPlayerSendPositionEvent event) {
        this.controller.onSendPosition(event);
    }

    private boolean matchesKeyword(String content) {
        String lowercaseContent = content.toLowerCase(Locale.ROOT);
        for (String keyword : this.keywordsSetting.getValue()) {
            if (!keyword.isBlank() && lowercaseContent.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public double getScanRange() {
        return this.scanRangeSetting.getValue();
    }

    public int getTrapdoorDelay() {
        return this.trapdoorDelaySetting.getValue();
    }

    public boolean isReturnHome() {
        return this.returnSetting.getValue();
    }

    public String getFoundMessage() {
        return this.foundMessageSetting.getValue();
    }

    public String getNotFoundMessage() {
        return this.notFoundMessageSetting.getValue();
    }

    public String getComingMessage() {
        return this.comingMessageSetting.getValue();
    }

    public String getDoneMessage() {
        return this.doneMessageSetting.getValue();
    }

    /**
     * Shows the reply locally and whispers it back to the requester through the configured command.
     * Blank replies are skipped entirely, which lets the player disable a single message.
     */
    public void reply(String sender, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ChatUtils.printChat(Component.literal(text), ChatUtils.moduleSignature(this));
        if (sender == null || sender.isBlank()) {
            return;
        }
        String template = this.replyCommandSetting.getValue();
        if (template == null || template.isBlank()) {
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return;
        }
        ClientPacketListener connection = player.connection;
        if (connection == null) {
            return;
        }
        String command = template.replace("{player}", sender).replace("{message}", text).trim();
        while (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            return;
        }
        connection.sendCommand(command);
    }

    public void notifyError(String key, Object... args) {
        String message = Translations.format(TRANSLATION_PREFIX + key, args);
        ChatUtils.printChat(Component.literal(message), ChatUtils.moduleSignature(this));
    }
}
