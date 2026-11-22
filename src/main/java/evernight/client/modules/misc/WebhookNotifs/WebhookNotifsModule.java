package evernight.client.modules.misc.WebhookNotifs;

import com.google.gson.JsonElement;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.managers.social.WebhookManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.NotificationMode;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.ui.ChatMessageEvent;
import evernight.client.events.ui.MinecraftTickEvent;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.StringSetting;

/**
 * Batches chat lines and pushes them to the destination configured in {@link WebhookManager}.
 *
 * <p>The delivery service is a {@link NotificationMode} setting; the credential rows below it are
 * projections of the manager state. They are excluded from the module config and from presets so
 * bot tokens and webhook URLs are never shared, and every edit is written straight back to
 * {@code webhook.json}.
 */
public class WebhookNotifsModule extends Module {
    private static final Map<Color, String> ANSI_PALETTE = new HashMap<>();

    static {
        ANSI_PALETTE.put(new Color(0, 0, 0), "30");
        ANSI_PALETTE.put(new Color(255, 0, 0), "31");
        ANSI_PALETTE.put(new Color(0, 255, 0), "32");
        ANSI_PALETTE.put(new Color(255, 170, 0), "33");
        ANSI_PALETTE.put(new Color(0, 0, 255), "34");
        ANSI_PALETTE.put(new Color(255, 0, 255), "35");
        ANSI_PALETTE.put(new Color(0, 255, 255), "36");
        ANSI_PALETTE.put(new Color(170, 170, 170), "37");
    }

    private final Setting<NotificationMode> modeSetting;
    private final Setting<String> urlSetting;
    private final Setting<String> telegramTokenSetting;
    private final Setting<String> telegramChatIdSetting;
    private final Setting<String> qqAppIdSetting;
    private final Setting<String> qqSecretSetting;
    private final Setting<String> qqOpenIdSetting;
    private final Setting<Float> timeoutSetting;
    private final Setting<Boolean> colorSetting;
    private final Setting<Boolean> timestampSetting;
    private final Setting<Boolean> ignoreEvernightSetting;
    private final Setting<Boolean> queueSetting;
    private final Setting<Boolean> disconnectSetting;
    private final Setting<Boolean> dMSetting;
    private final Setting<Boolean> chatMessagesSetting;
    private final Setting<Boolean> visualRangeSetting;
    private final Setting<Boolean> stashSetting;
    private final Setting<Boolean> pingSetting;
    private final DateTimeFormatter timestampFormatter;
    private final List<String> pendingMessages;
    private final List<String> includedMessages;
    private final ElapsedTimer notificationTimer;
    private boolean pingPending;
    private String previousQueueMessage;
    private ServerData lastServer;
    private boolean syncingSettings;
    private long lastQqBindingRevision;

    public WebhookNotifsModule() {
        super("WebhookNotifs", ModuleCategory.MISC,
                "DiscordNotifs");
        this.modeSetting = this.addCredentialSetting(new EnumSetting<>("Mode", NotificationMode.DISCORD));
        this.urlSetting = this.addCredentialSetting(new StringSetting("Url", ""));
        this.telegramTokenSetting = this.addCredentialSetting(new StringSetting("TelegramToken", ""));
        this.telegramChatIdSetting = this.addCredentialSetting(new StringSetting("TelegramChatId", ""));
        this.qqAppIdSetting = this.addCredentialSetting(new StringSetting("QqAppId", ""));
        this.qqSecretSetting = this.addCredentialSetting(new StringSetting("QqSecret", ""));
        this.qqOpenIdSetting = this.addCredentialSetting(new StringSetting("QqOpenId", ""));
        this.urlSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.DISCORD);
        this.telegramTokenSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.TELEGRAM);
        this.telegramChatIdSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.TELEGRAM);
        this.qqAppIdSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.QQ);
        this.qqSecretSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.QQ);
        this.qqOpenIdSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.QQ);
        this.timeoutSetting = this.add(new NumberSetting<>("Timeout", 1.0F, 1.0F, 10.0F).withUnit("s"));
        this.colorSetting = this.add(new BooleanSetting("Color", false));
        this.colorSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.DISCORD);
        this.timestampSetting = this.add(new BooleanSetting("Timestamp", false));
        this.ignoreEvernightSetting = this.add(new BooleanSetting("IgnoreEvernight", true));
        BooleanSetting types = new BooleanSetting("Types", false);
        types.markAsGroup();
        types.markVisibilityDependency();
        Setting<Boolean> registeredTypes = this.add(types);
        this.queueSetting = this.add(new BooleanSetting("Queue", true).dependsOn(registeredTypes));
        this.disconnectSetting = this.add(new BooleanSetting("Disconnect", true).dependsOn(registeredTypes));
        this.dMSetting = this.add(new BooleanSetting("DM", true).dependsOn(registeredTypes));
        this.chatMessagesSetting = this.add(new BooleanSetting("ChatMessages", false).dependsOn(registeredTypes));
        this.visualRangeSetting = this.add(new BooleanSetting("VisualRange", false).dependsOn(registeredTypes));
        BooleanSetting stash = new BooleanSetting("Stash", false);
        stash.markVisibilityDependency();
        Setting<Boolean> registeredStash = this.add(stash.dependsOn(registeredTypes));
        this.stashSetting = registeredStash;
        this.pingSetting = this.add(new BooleanSetting("Ping", false).dependsOn(registeredTypes, registeredStash));
        this.pingSetting.addVisibilityPredicate(value -> this.getMode() == NotificationMode.DISCORD);
        this.timestampFormatter = DateTimeFormatter.ofPattern("HH:mm");
        this.pendingMessages = Collections.synchronizedList(new ArrayList<>());
        this.includedMessages = new ArrayList<>();
        this.notificationTimer = new ElapsedTimer();
    }

    private static <T> void applySetting(Setting<T> setting, T value) {
        if (value != null && !Objects.equals(setting.getValue(), value)) {
            setting.setValue(value);
        }
    }

    public static String getClosestAnsiColor(int rgb) {
        String closestCode = "37";
        double closestDistance = Double.MAX_VALUE;
        for (Map.Entry<Color, String> entry : ANSI_PALETTE.entrySet()) {
            Color color = entry.getKey();
            double red = color.getRed() - ARGB.red(rgb);
            double green = color.getGreen() - ARGB.green(rgb);
            double blue = color.getBlue() - ARGB.blue(rgb);
            double distance = Math.pow(red, 2.0D) + Math.pow(green, 2.0D) + Math.pow(blue, 2.0D);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestCode = entry.getValue();
            }
        }
        return closestCode;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null) {
            super.fromJson(json);
        }
        this.syncSettingsFromManager();
    }

    @Override
    public void onEnable() {
        this.syncSettingsFromManager();
        this.notificationTimer.reset();
        WebhookManager manager = ClientContext.webhookManager;
        if (manager == null) return;
        this.lastQqBindingRevision = manager.getQqBindingRevision();
        manager.ensureQqGateway();
        if (manager.isConfigured()) return;
        String prefix = CommandRegistry.getPrefix();
        ChatUtils.printChat(Component.literal(Translations.format("modules.WebhookNotifs.NotConfigured", prefix)),
                ChatUtils.createSignature(-10395), ChatUtils.MessagePriority.MID);
    }

    @Override
    public void onDisable() {
        WebhookManager manager = ClientContext.webhookManager;
        if (manager != null) manager.stopQqGateway();
    }

    /**
     * Copies the persisted {@link WebhookManager} state into the credential settings so the ClickGUI
     * shows what is actually configured. Credential settings are excluded from the module config, so
     * this is the only path that populates them at startup and after a {@code webhook} command.
     */
    public void syncSettingsFromManager() {
        WebhookManager manager = ClientContext.webhookManager;
        if (manager == null) {
            return;
        }
        this.syncingSettings = true;
        try {
            applySetting(this.modeSetting, manager.getMode());
            applySetting(this.urlSetting, manager.getUrl());
            applySetting(this.telegramTokenSetting, manager.getTelegramBotToken());
            applySetting(this.telegramChatIdSetting, manager.getTelegramChatId());
            applySetting(this.qqAppIdSetting, manager.getQqAppId());
            applySetting(this.qqSecretSetting, manager.getQqClientSecret());
            applySetting(this.qqOpenIdSetting, manager.getQqOpenId());
        } finally {
            this.syncingSettings = false;
        }
    }

    private void pushSettingsToManager() {
        if (this.syncingSettings) {
            return;
        }
        WebhookManager manager = ClientContext.webhookManager;
        if (manager == null) {
            return;
        }
        manager.setMode(this.modeSetting.getValue());
        try {
            manager.setUrl(this.urlSetting.getValue());
        } catch (IllegalArgumentException invalidUrl) {
            // Keep the previously valid webhook until the edited text parses again.
        }
        try {
            manager.setTelegram(this.telegramTokenSetting.getValue(), this.telegramChatIdSetting.getValue());
        } catch (IllegalArgumentException invalidTelegramCredentials) {
            // Keep the previously valid Telegram credentials.
        }
        try {
            manager.setQq(this.qqAppIdSetting.getValue(), this.qqSecretSetting.getValue(),
                    this.qqOpenIdSetting.getValue());
        } catch (IllegalArgumentException invalidQqCredentials) {
            // Keep the previously valid QQ credentials.
        }
        manager.ensureQqGateway();
    }

    private NotificationMode getMode() {
        NotificationMode mode = this.modeSetting.getValue();
        return mode == null ? NotificationMode.DISCORD : mode;
    }

    private String formatMessage(ChatMessageEvent event) {
        if (!this.colorSetting.getValue()) return event.getOriginalText();
        StringBuilder message = new StringBuilder();
        int[] previousColor = {-1};
        event.getOriginalMessage().getVisualOrderText().accept((index, style, codePoint) -> {
            int color = style.getColor() == null ? -1 : style.getColor().getValue();
            if (color != previousColor[0]) message.append("\u001b[").append(getClosestAnsiColor(color)).append("m");
            message.appendCodePoint(codePoint);
            previousColor[0] = color;
            return true;
        });
        return message.toString();
    }

    @EventHandler
    private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        if (this.disconnectSetting.getValue() && this.lastServer != null) {
            this.enqueueLines(Translations.format("modules.WebhookNotifs.Disconnected", this.lastServer.ip).split("\n"));
            this.lastServer = null;
        }
    }

    private void enqueueLines(String[] lines) {
        StringBuilder message = new StringBuilder();
        for (String line : lines) {
            if (!line.isEmpty()) message.append(ChatFormatting.stripFormatting(line)).append('\n');
        }
        if (message.length() >= 2) this.pendingMessages.add(message.substring(0, message.length() - 1));
    }

    @EventHandler
    private void onChatMessage(ChatMessageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (ClientContext.webhookManager == null || !ClientContext.webhookManager.isConfigured()
                || event.getStage() != EventStage.Pre || event.getOriginalText() == null)
            return;
        if (this.ignoreEvernightSetting.getValue() && event.getTag() == ChatUtils.MESSAGE_TAG) return;
        String message = this.formatMessage(event);
        String[] lines = message.split(("\n"));
        boolean incomingDirectMessage = ChatUtils.isIncomingWhisper(message);
        boolean outgoingDirectMessage = ChatUtils.isOutgoingWhisper(message);
        boolean stashMessage = this.isStashMessage(event);
        boolean visualRangeMessage = this.isVisualRangeMessage(event);
        if (incomingDirectMessage || outgoingDirectMessage) {
            Setting<Boolean> directMessageOption = this.dMSetting;
            Boolean notifyDirectMessages = directMessageOption.getValue();
            if (notifyDirectMessages) {
                this.enqueueLines(lines);
            }
            return;
        }
        if (message.contains("Position in queue:")) {
            int lineCount = lines.length;
            if (lineCount > (15)) {
                if (this.previousQueueMessage == null) {
                    this.previousQueueMessage = message;
                } else if (!this.previousQueueMessage.equals(message)) {
                    Setting<Boolean> queueOption = this.queueSetting;
                    Boolean notifyQueue = queueOption.getValue();
                    if (notifyQueue) {
                        this.enqueueLines(lines);
                        this.previousQueueMessage = message;
                    }
                    return;
                }
                return;
            }
        }
        Setting<Boolean> chatMessagesOption = this.chatMessagesSetting;
        Boolean notifyChatMessages = chatMessagesOption.getValue();
        if (notifyChatMessages || stashMessage || visualRangeMessage) {
            if (stashMessage) {
                Setting<Boolean> pingOption = this.pingSetting;
                Boolean pingEveryone = pingOption.getValue();
                if (pingEveryone) {
                    this.pingPending = true;
                }
            }
            this.enqueueLines(lines);
        }
    }

    private boolean isVisualRangeMessage(ChatMessageEvent event) {
        return this.visualRangeSetting.getValue() && event.getSignature() != null
                && event.getSignature().asByteBuffer().getInt() <= 0
                && event.getMessage().getString().contains(
                Translations.ui("modules.VisualRange.MatchPhrase"));
    }

    private boolean isStashMessage(ChatMessageEvent event) {
        return this.stashSetting.getValue() && event.getSignature() != null
                && event.getSignature().asByteBuffer().getInt() == -13579;
    }

    @EventHandler
    private void onClientUpdate(MinecraftTickEvent event) {
        if (ClientAccess.evernightMinecraft.level != null)
            this.lastServer = ClientContext.serverStateManager.getServerData();
        WebhookManager manager = ClientContext.webhookManager;
        if (manager == null) return;
        manager.ensureQqGateway();
        long bindingRevision = manager.getQqBindingRevision();
        if (bindingRevision != this.lastQqBindingRevision) {
            this.lastQqBindingRevision = bindingRevision;
            this.syncSettingsFromManager();
            ChatUtils.printChat(Component.literal(Translations.format("modules.WebhookNotifs.QqBound", manager.getQqOpenId())), ChatUtils.createSignature(-10396),
                    ChatUtils.MessagePriority.MID);
        }
        if (!manager.isConfigured()
                || !this.notificationTimer.hasElapsed(this.timeoutSetting.getValue(), TimeUnit.SECONDS)
                || this.pendingMessages.isEmpty()) return;
        this.notificationTimer.reset();
        synchronized (this.pendingMessages) {
            if (this.pendingMessages.isEmpty()) return;
            boolean mentionEveryone = this.pingPending && manager.getMode() == NotificationMode.DISCORD;
            this.pingPending = false;
            StringBuilder batch = new StringBuilder();
            String timestamp = "[" + this.timestampFormatter.format(LocalDateTime.now()) + "] ";
            for (String message : this.pendingMessages) {
                if (batch.length() > 1900) break;
                if (this.timestampSetting.getValue()) batch.append(timestamp);
                batch.append(message).append('\n');
                this.includedMessages.add(message);
            }
            String text = batch.substring(0, batch.length() - 1).replace("`", "'");
            if (manager.getMode() == NotificationMode.DISCORD) {
                String mention = mentionEveryone ? "@everyone\n" : "";
                String language = this.colorSetting.getValue() ? "ansi\n" : "";
                manager.sendContent(mention + "```" + language + text + "```");
            } else {
                manager.sendContent(text);
            }
            for (String message : this.includedMessages) this.pendingMessages.remove(message);
            this.includedMessages.clear();
        }
    }

    private <S extends Setting<?>> S addCredentialSetting(S setting) {
        setting.setExcludedFromConfig(true);
        setting.setChangeCallback(this::pushSettingsToManager);
        return this.add(setting);
    }
}
