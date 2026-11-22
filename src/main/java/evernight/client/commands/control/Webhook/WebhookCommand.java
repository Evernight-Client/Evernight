package evernight.client.commands.control.Webhook;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.social.WebhookManager;
import evernight.client.modules.misc.WebhookNotifs.WebhookNotifsModule;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.NotificationMode;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.network.chat.Component;

import java.net.URI;
import evernight.client.commands.arguments.FancyStringArgument;
import evernight.client.commands.Command;

/**
 * Configures the WebhookNotifs destination. Every delivery service is a {@link NotificationMode};
 * {@code set}/{@code telegram}/{@code qq} switch to the matching mode and store its credentials in
 * {@code webhook.json}, then mirror the result into the module settings for the ClickGUI.
 */
public final class WebhookCommand extends Command {
    public WebhookCommand() {
        super("webhook");
    }

    private static void syncModuleSettings() {
        WebhookNotifsModule module = ClientContext.getModule(WebhookNotifsModule.class);
        if (module != null) {
            module.syncSettingsFromManager();
        }
    }

    private static String describeDestination() {
        WebhookManager manager = ClientContext.webhookManager;
        NotificationMode mode = manager.getMode();
        return Translations.format("commands.Webhook.Description",
                mode.getDisplayName(), describeDestinationValue(mode, manager));
    }

    private static String describeDestinationValue(NotificationMode mode, WebhookManager manager) {
        String missing = Translations.ui("commands.Webhook.DestinationMissing");
        return switch (mode) {
            case DISCORD -> manager.getUrl().isBlank() ? missing
                    : Translations.format("commands.Webhook.DestinationWebhook", hostOf(manager.getUrl()));
            case TELEGRAM -> manager.getTelegramChatId().isBlank() ? missing
                    : Translations.format("commands.Webhook.DestinationTelegram",
                    manager.getTelegramChatId());
            case QQ -> manager.getQqOpenId().isBlank()
                    ? (manager.hasQqCredentials()
                    ? Translations.ui("commands.Webhook.DestinationQqWaiting")
                    : missing)
                    : Translations.format("commands.Webhook.DestinationQq", manager.getQqOpenId());
        };
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException invalid) {
            return url;
        }
    }

    private static void reply(String message, int signature) {
        ChatUtils.printChat(Component.literal(message), signature);
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        builder.then(literal("get").executes(context -> {
            reply(describeDestination(), -1035361);
            return 1;
        }));
        builder.then(literal("mode").then(argument("mode", FancyStringArgument.fancyString()).executes(context -> {
            String requested = (String) context.getArgument("mode", String.class);
            NotificationMode mode = NotificationMode.fromName(requested);
            if (mode == null) {
                reply(Translations.ui("commands.Webhook.InvalidMode"), -1035362);
                return 1;
            }
            WebhookManager manager = ClientContext.webhookManager;
            manager.setMode(mode);
            syncModuleSettings();
            reply(Translations.format("commands.Webhook.ModeUpdated", mode.getDisplayName(), describeDestinationValue(mode, manager)),
                    -1035362);
            return 1;
        })));
        builder.then(literal("set").then(argument("url", StringArgumentType.greedyString()).executes(context -> {
            String url = ((String) context.getArgument("url", String.class)).trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
            WebhookManager manager = ClientContext.webhookManager;
            try {
                WebhookManager.TelegramEndpoint telegram = WebhookManager.parseTelegramUrl(url);
                if (telegram != null) {
                    if (telegram.chatId().isEmpty()) {
                        throw new IllegalArgumentException("Telegram URL without chat_id");
                    }
                    manager.setTelegram(telegram.botToken(), telegram.chatId());
                    manager.setMode(NotificationMode.TELEGRAM);
                } else {
                    manager.setUrl(url);
                    manager.setMode(NotificationMode.DISCORD);
                }
                syncModuleSettings();
                reply(Translations.format("commands.Webhook.DestinationUpdated", describeDestination()), -1035362);
            } catch (IllegalArgumentException invalid) {
                reply(Translations.ui("commands.Webhook.InvalidUrl"),
                        -1035362);
            }
            return 1;
        })));
        builder.then(literal("telegram").then(argument("botToken", FancyStringArgument.fancyString())
                .then(argument("chatId", FancyStringArgument.fancyString()).executes(context -> {
                    String botToken = (String) context.getArgument("botToken", String.class);
                    String chatId = (String) context.getArgument("chatId", String.class);
                    try {
                        if (botToken.isBlank() || chatId.isBlank()) {
                            throw new IllegalArgumentException("Missing Telegram credentials");
                        }
                        WebhookManager manager = ClientContext.webhookManager;
                        manager.setTelegram(botToken, chatId);
                        manager.setMode(NotificationMode.TELEGRAM);
                        syncModuleSettings();
                        reply(Translations.ui("commands.Webhook.TelegramConfigured"), -1035362);
                    } catch (IllegalArgumentException invalid) {
                        reply(Translations.ui("commands.Webhook.InvalidTelegramCredentials"), -1035362);
                    }
                    return 1;
                }))));
        builder.then(literal("qq").then(argument("appId", FancyStringArgument.fancyString())
                .then(argument("clientSecret", FancyStringArgument.fancyString())
                        .then(argument("openId", FancyStringArgument.fancyString()).executes(context -> {
                            String appId = (String) context.getArgument("appId", String.class);
                            String clientSecret = (String) context.getArgument("clientSecret", String.class);
                            String openId = (String) context.getArgument("openId", String.class);
                            try {
                                if (appId.isBlank() || clientSecret.isBlank() || openId.isBlank()) {
                                    throw new IllegalArgumentException("Missing QQ bot credentials");
                                }
                                WebhookManager manager = ClientContext.webhookManager;
                                manager.setQq(appId, clientSecret, openId);
                                manager.setMode(NotificationMode.QQ);
                                syncModuleSettings();
                                reply(Translations.ui("commands.Webhook.QqConfigured"), -1035362);
                            } catch (IllegalArgumentException invalid) {
                                reply(Translations.ui("commands.Webhook.InvalidQqCredentials"), -1035362);
                            }
                            return 1;
                        })))));
        builder.then(literal("openid").then(argument("id", FancyStringArgument.fancyString()).executes(context -> {
            WebhookManager manager = ClientContext.webhookManager;
            if (manager.getMode() != NotificationMode.QQ) {
                reply(Translations.ui("commands.Webhook.OpenIdUnsupported"), -1);
                return 1;
            }
            String openId = (String) context.getArgument("id", String.class);
            try {
                manager.setQq(manager.getQqAppId(), manager.getQqClientSecret(), openId);
                syncModuleSettings();
                ChatUtils.printChat(Component.literal(Translations.ui("commands.Webhook.OpenIdUpdatedPrefix")).append(this.formatText(openId)), ChatUtils.DEFAULT_SIGNATURE);
            } catch (IllegalArgumentException invalid) {
                reply(Translations.ui("commands.Webhook.InvalidOpenId"), -1);
            }
            return 1;
        })).executes(context -> {
            WebhookManager manager = ClientContext.webhookManager;
            if (manager.getMode() != NotificationMode.QQ) {
                reply(Translations.ui("commands.Webhook.OpenIdUnsupported"), -1);
                return 1;
            }
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Webhook.OpenIdIsPrefix")).append(this.formatText(manager.getQqOpenId())), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        }));
        builder.then(literal("chatid").then(argument("id", FancyStringArgument.fancyString()).executes(context -> {
            WebhookManager manager = ClientContext.webhookManager;
            if (manager.getMode() != NotificationMode.TELEGRAM) {
                reply(Translations.ui("commands.Webhook.ChatIdUnsupported"), -1);
                return 1;
            }
            String chatId = (String) context.getArgument("id", String.class);
            try {
                manager.setTelegramChatId(chatId);
                syncModuleSettings();
                ChatUtils.printChat(Component.literal(Translations.ui("commands.Webhook.ChatIdUpdatedPrefix")).append(this.formatText(chatId)), ChatUtils.DEFAULT_SIGNATURE);
            } catch (IllegalArgumentException invalid) {
                reply(Translations.ui("commands.Webhook.InvalidChatId"), -1);
            }
            return 1;
        })).executes(context -> {
            WebhookManager manager = ClientContext.webhookManager;
            if (manager.getMode() != NotificationMode.TELEGRAM) {
                reply(Translations.ui("commands.Webhook.ChatIdUnsupported"), -1);
                return 1;
            }
            ChatUtils.printChat(Component.literal(Translations.ui("commands.Webhook.ChatIdIsPrefix")).append(this.formatText(manager.getTelegramChatId())), ChatUtils.DEFAULT_SIGNATURE);
            return 1;
        }));
        builder.then(literal("clear").executes(context -> {
            ClientContext.webhookManager.clear();
            syncModuleSettings();
            reply(Translations.ui("commands.Webhook.DestinationCleared"), -1035363);
            return 1;
        }));
    }
}
