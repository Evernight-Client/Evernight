package evernight.client.managers.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import evernight.client.ClientAccess;
import evernight.client.EvernightClient;
import evernight.client.settings.core.JsonConfigurable;
import evernight.client.settings.options.NotificationMode;
import evernight.client.utils.network.HttpRequests;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persisted notification destination plus the transport that pushes batched chat lines to it.
 *
 * <p>Three delivery services share this single configuration file ({@code webhook.json}); the active
 * one is {@link NotificationMode}: a Discord-compatible webhook, a Telegram bot or the official QQ
 * bot (single chat only). The ClickGUI settings of
 * {@link evernight.client.modules.misc.WebhookNotifs.WebhookNotifsModule} are projections of this state:
 * they push every edit here and are resynchronised from here after config loading, so credentials
 * never end up in {@code modules.json} or in shareable presets.
 *
 * <p>All network work is asynchronous; the QQ OpenAPI access token is cached and refreshed one
 * minute before it expires.
 */
public final class WebhookManager implements JsonConfigurable {
    private static final Pattern TELEGRAM_PATH = Pattern.compile("/bot[0-9]+:[A-Za-z0-9_-]+/sendMessage",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TELEGRAM_BOT_TOKEN = Pattern.compile("/bot([0-9]+:[A-Za-z0-9_-]+)/sendMessage",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BOT_TOKEN = Pattern.compile("[0-9]+:[A-Za-z0-9_-]+");
    private static final Pattern CHAT_ID = Pattern.compile("-?[0-9]+|@[A-Za-z0-9_]+");
    private static final Pattern QQ_APP_ID = Pattern.compile("[0-9]{1,32}");
    private static final Pattern QQ_OPEN_ID = Pattern.compile("[A-Za-z0-9_=+/.-]{1,256}");
    private static final Pattern ANSI_COLOR = Pattern.compile("\\x1B\\[[0-9;]*m");
    private static final String TELEGRAM_ENDPOINT = "https://api.telegram.org/bot%s/sendMessage";
    private static final String QQ_API_BASE = "https://api.sgroup.qq.com";
    private static final List<URI> QQ_TOKEN_ENDPOINTS = List.of(
            URI.create("https://api.bot.qq.com/app/getAppAccessToken"),
            URI.create("https://bots.qq.com/app/getAppAccessToken"));
    private static final int WEBHOOK_CONTENT_LIMIT = 2000;
    private static final int TELEGRAM_CONTENT_LIMIT = 4096;
    private static final int QQ_CONTENT_LIMIT = 2000;
    private static final long QQ_TOKEN_REFRESH_MARGIN_MILLIS = 60_000L;
    private final QqGatewayClient qqGateway = new QqGatewayClient(this);
    private volatile NotificationMode mode = NotificationMode.DISCORD;
    private volatile String url = "";
    private volatile String telegramBotToken = "";
    private volatile String telegramChatId = "";
    private volatile String qqAppId = "";
    private volatile String qqClientSecret = "";
    private volatile String qqOpenId = "";
    private volatile String qqAccessToken;
    private volatile long qqAccessTokenExpiresAt;
    private volatile CompletableFuture<String> qqAccessTokenRequest;
    private volatile long qqBindingRevision;

    /**
     * Parses a legacy Telegram bot URL of the shape
     * {@code https://api.telegram.org/bot<token>/sendMessage?chat_id=<id>}.
     *
     * @return the extracted credentials, or {@code null} when the URL is not a Telegram endpoint
     * @throws IllegalArgumentException when the URL is a malformed Telegram endpoint
     */
    public static TelegramEndpoint parseTelegramUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException invalidUrl) {
            return null;
        }
        if (!"api.telegram.org".equalsIgnoreCase(uri.getHost())) {
            return null;
        }
        try {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getPort() != -1 && uri.getPort() != 443
                    || !TELEGRAM_PATH.matcher(uri.getPath()).matches()) {
                throw new IllegalArgumentException("Invalid Telegram endpoint");
            }
            Matcher tokenMatcher = TELEGRAM_BOT_TOKEN.matcher(uri.getPath());
            if (!tokenMatcher.find()) {
                throw new IllegalArgumentException("Invalid Telegram bot token");
            }
            String chatId = "";
            String query = uri.getRawQuery();
            if (query != null) {
                for (String parameter : query.split("&")) {
                    String[] pair = parameter.split("=", 2);
                    if (pair.length == 2 && URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals("chat_id")) {
                        chatId = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        if (!chatId.isEmpty()) {
                            validateChatId(chatId);
                        }
                        break;
                    }
                }
            }
            return new TelegramEndpoint(tokenMatcher.group(1), chatId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid Telegram endpoint");
        }
    }

    private static void validateChatId(String chatId) {
        if (chatId == null || !CHAT_ID.matcher(chatId).matches()) {
            throw new IllegalArgumentException("Invalid Telegram chat ID");
        }
    }

    private static HttpRequest.Builder jsonRequest(URI endpoint, String body) {
        return HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("User-Agent", EvernightClient.USER_AGENT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(BodyPublishers.ofString(body));
    }

    private static String describeFailure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private static List<String> splitByCodePoints(String text, int limit) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int counted = 0;
            while (end < text.length() && counted < limit) {
                end += Character.charCount(text.codePointAt(end));
                counted++;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String validateWebhookUrl(String url) {
        try {
            URI uri = URI.create(url);
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid webhook URL");
            }
            return uri.toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid webhook URL");
        }
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return Objects.toString(object.get(key).getAsString(), "").trim();
        } catch (RuntimeException invalidValue) {
            return "";
        }
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!(json instanceof JsonObject object)) {
            return;
        }
        String storedUrl = readString(object, "url");
        String telegramToken = "";
        String telegramChat = "";
        if (object.get("telegram") instanceof JsonObject telegram) {
            telegramToken = readString(telegram, "botToken");
            telegramChat = readString(telegram, "chatId");
        }
        if (telegramChat.isEmpty() && object.has("telegramChatId")) {
            telegramChat = readString(object, "telegramChatId");
        }
        String storedAppId = "";
        String storedSecret = "";
        String storedOpenId = "";
        if (object.get("qq") instanceof JsonObject qq) {
            storedAppId = readString(qq, "appId");
            storedSecret = readString(qq, "clientSecret");
            storedOpenId = readString(qq, "openId");
        }
        if (!storedUrl.isEmpty()) {
            try {
                TelegramEndpoint legacyEndpoint = parseTelegramUrl(storedUrl);
                if (legacyEndpoint != null) {
                    if (telegramToken.isEmpty()) {
                        telegramToken = legacyEndpoint.botToken();
                    }
                    if (telegramChat.isEmpty()) {
                        telegramChat = legacyEndpoint.chatId();
                    }
                    storedUrl = "";
                }
            } catch (IllegalArgumentException invalidLegacyUrl) {
                storedUrl = "";
            }
        }
        this.url = storedUrl;
        this.telegramBotToken = telegramToken;
        this.telegramChatId = telegramChat;
        this.qqAppId = storedAppId;
        this.qqClientSecret = storedSecret;
        this.qqOpenId = storedOpenId;
        this.invalidateQqAccessToken();
        NotificationMode storedMode = NotificationMode.fromName(readString(object, "mode"));
        this.mode = storedMode != null ? storedMode : this.inferMode();
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("mode", this.mode.getName());
        json.addProperty("url", this.url);
        JsonObject telegram = new JsonObject();
        telegram.addProperty("botToken", this.telegramBotToken);
        telegram.addProperty("chatId", this.telegramChatId);
        json.add("telegram", telegram);
        JsonObject qq = new JsonObject();
        qq.addProperty("appId", this.qqAppId);
        qq.addProperty("clientSecret", this.qqClientSecret);
        qq.addProperty("openId", this.qqOpenId);
        json.add("qq", qq);
        return json;
    }

    @Override
    public String getConfigName() {
        return "webhook.json";
    }

    public NotificationMode getMode() {
        return this.mode;
    }

    public void setMode(NotificationMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    public String getUrl() {
        return this.url;
    }

    /**
     * Stores a Discord-compatible webhook URL.
     *
     * @throws IllegalArgumentException when the URL is not an absolute http(s) endpoint or points at
     *                                  the Telegram Bot API, which is configured as its own mode
     */
    public void setUrl(String url) {
        if (url == null || url.isBlank()) {
            this.url = "";
            return;
        }
        String trimmed = url.trim();
        if (parseTelegramUrl(trimmed) != null) {
            throw new IllegalArgumentException("Telegram endpoints are configured in Telegram mode");
        }
        this.url = validateWebhookUrl(trimmed);
    }

    public String getTelegramBotToken() {
        return this.telegramBotToken;
    }

    public String getTelegramChatId() {
        return this.telegramChatId;
    }

    public void setTelegramChatId(String chatId) {
        validateChatId(chatId);
        this.telegramChatId = chatId;
    }

    /**
     * Stores Telegram bot credentials. Blank halves are accepted so the ClickGUI can be filled in
     * field by field; {@link #isConfigured()} only reports true once both are present.
     *
     * @throws IllegalArgumentException when a non-blank half does not look like a bot token or chat id
     */
    public void setTelegram(String botToken, String chatId) {
        String trimmedToken = botToken == null ? "" : botToken.trim();
        String trimmedChatId = chatId == null ? "" : chatId.trim();
        if (!trimmedToken.isEmpty() && !BOT_TOKEN.matcher(trimmedToken).matches()) {
            throw new IllegalArgumentException("Invalid Telegram bot token");
        }
        if (!trimmedChatId.isEmpty()) {
            validateChatId(trimmedChatId);
        }
        this.telegramBotToken = trimmedToken;
        this.telegramChatId = trimmedChatId;
    }

    public String getQqAppId() {
        return this.qqAppId;
    }

    public String getQqClientSecret() {
        return this.qqClientSecret;
    }

    public String getQqOpenId() {
        return this.qqOpenId;
    }

    /**
     * Stores QQ official bot credentials and the single-chat openid that receives the messages.
     * Blank values are accepted for the same reason as {@link #setTelegram(String, String)};
     * {@link #isConfigured()} stays false until all three parts are present.
     *
     * @throws IllegalArgumentException when a non-blank AppID or openid contains characters that
     *                                  cannot be sent to the OpenAPI
     */
    public void setQq(String appId, String clientSecret, String openId) {
        String trimmedAppId = appId == null ? "" : appId.trim();
        String trimmedOpenId = openId == null ? "" : openId.trim();
        if (!trimmedAppId.isEmpty() && !QQ_APP_ID.matcher(trimmedAppId).matches()) {
            throw new IllegalArgumentException("Invalid QQ bot AppID");
        }
        if (!trimmedOpenId.isEmpty() && !QQ_OPEN_ID.matcher(trimmedOpenId).matches()) {
            throw new IllegalArgumentException("Invalid QQ user openid");
        }
        String trimmedSecret = clientSecret == null ? "" : clientSecret.trim();
        boolean credentialsChanged = !trimmedAppId.equals(this.qqAppId) || !trimmedSecret.equals(this.qqClientSecret);
        this.qqAppId = trimmedAppId;
        this.qqClientSecret = trimmedSecret;
        this.qqOpenId = trimmedOpenId;
        if (credentialsChanged) {
            this.invalidateQqAccessToken();
        }
    }

    public boolean isConfigured() {
        return switch (this.mode) {
            case DISCORD -> isHttpUrl(this.url);
            case TELEGRAM -> !this.telegramBotToken.isBlank() && !this.telegramChatId.isBlank();
            case QQ -> !this.qqAppId.isBlank() && !this.qqClientSecret.isBlank() && !this.qqOpenId.isBlank();
        };
    }

    public boolean hasQqCredentials() {
        return !this.qqAppId.isBlank() && !this.qqClientSecret.isBlank();
    }

    /**
     * Adopts the sender of a single-chat message as the destination while none is configured.
     * Called from the gateway thread; later messages never overwrite an existing openid.
     */
    void bindQqOpenId(String openId) {
        if (openId == null || openId.isBlank() || !QQ_OPEN_ID.matcher(openId.trim()).matches()) {
            return;
        }
        synchronized (this) {
            if (!this.qqOpenId.isBlank()) {
                return;
            }
            this.qqOpenId = openId.trim();
            this.qqBindingRevision++;
        }
        ClientAccess.logger.info("QQ single chat bound as the notification destination");
    }

    /**
     * Bumped whenever {@link #bindQqOpenId(String)} adopts a destination, so the settings UI can be
     * resynchronised without polling every field.
     */
    public long getQqBindingRevision() {
        return this.qqBindingRevision;
    }

    /**
     * Runs the gateway listener while the QQ mode is active and credentials are present; it is what
     * makes the empty-openid auto binding possible. Repeated calls are no-ops.
     */
    public void ensureQqGateway() {
        if (this.mode == NotificationMode.QQ && this.hasQqCredentials()) {
            this.qqGateway.start();
        } else {
            this.qqGateway.stop();
        }
    }

    public void stopQqGateway() {
        this.qqGateway.stop();
    }

    public boolean isQqGatewayRunning() {
        return this.qqGateway.isRunning();
    }

    /**
     * Clears the credentials of the currently selected mode, keeping the other modes configured.
     */
    public void clear() {
        switch (this.mode) {
            case DISCORD -> this.url = "";
            case TELEGRAM -> {
                this.telegramBotToken = "";
                this.telegramChatId = "";
            }
            case QQ -> {
                this.qqAppId = "";
                this.qqClientSecret = "";
                this.qqOpenId = "";
                this.invalidateQqAccessToken();
            }
        }
    }

    /**
     * Sends already formatted text to the active destination. Text is split to the service limit and
     * ANSI colour codes are stripped for services that render plain text.
     */
    public void sendContent(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        NotificationMode current = this.mode;
        if (current == NotificationMode.QQ) {
            this.sendQqContent(content);
            return;
        }
        List<HttpRequest> requests = current == NotificationMode.TELEGRAM
                ? this.createTelegramRequests(content)
                : this.createWebhookRequests(content);
        this.deliver(requests);
    }

    private NotificationMode inferMode() {
        if (!this.qqAppId.isBlank() && !this.qqClientSecret.isBlank() && !this.qqOpenId.isBlank()) {
            return NotificationMode.QQ;
        }
        return !this.telegramBotToken.isBlank() ? NotificationMode.TELEGRAM : NotificationMode.DISCORD;
    }

    private List<HttpRequest> createWebhookRequests(String content) {
        if (!isHttpUrl(this.url)) {
            return List.of();
        }
        URI endpoint = URI.create(this.url);
        List<HttpRequest> requests = new ArrayList<>();
        for (String chunk : splitByCodePoints(content, WEBHOOK_CONTENT_LIMIT)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("content", chunk);
            requests.add(jsonRequest(endpoint, payload.toString()).build());
        }
        return List.copyOf(requests);
    }

    private List<HttpRequest> createTelegramRequests(String content) {
        String token = this.telegramBotToken;
        String chatId = this.telegramChatId;
        if (token.isBlank() || chatId.isBlank() || !BOT_TOKEN.matcher(token).matches()) {
            return List.of();
        }
        String text = ANSI_COLOR.matcher(content).replaceAll("");
        if (text.isEmpty()) {
            return List.of();
        }
        URI endpoint = URI.create(String.format(TELEGRAM_ENDPOINT, token));
        List<HttpRequest> requests = new ArrayList<>();
        for (String chunk : splitByCodePoints(text, TELEGRAM_CONTENT_LIMIT)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("chat_id", chatId);
            payload.addProperty("text", chunk);
            requests.add(jsonRequest(endpoint, payload.toString()).build());
        }
        return List.copyOf(requests);
    }

    private List<HttpRequest> createQqRequests(String accessToken, String content) {
        String openId = this.qqOpenId;
        String appId = this.qqAppId;
        if (openId.isBlank() || appId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        URI endpoint = URI.create(QQ_API_BASE + "/v2/users/" + encodePathSegment(openId) + "/messages");
        String text = ANSI_COLOR.matcher(content).replaceAll("");
        List<HttpRequest> requests = new ArrayList<>();
        for (String chunk : splitByCodePoints(text, QQ_CONTENT_LIMIT)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("msg_type", 0);
            payload.addProperty("content", chunk);
            requests.add(jsonRequest(endpoint, payload.toString())
                    .header("Authorization", "QQBot " + accessToken)
                    .header("X-Union-Appid", appId)
                    .build());
        }
        return List.copyOf(requests);
    }

    private void sendQqContent(String content) {
        if (!this.isConfigured()) {
            return;
        }
        String appId = this.qqAppId;
        this.requestQqAccessToken().thenCompose(token -> {
            this.deliver(this.createQqRequests(token, content));
            return CompletableFuture.completedFuture(null);
        }).exceptionally(failure -> {
            ClientAccess.logger.warn("QQ bot notification failed ({})", describeFailure(failure));
            if (appId.equals(this.qqAppId)) {
                this.invalidateQqAccessToken();
            }
            return null;
        });
    }

    private void deliver(List<HttpRequest> requests) {
        CompletableFuture<Void> delivery = CompletableFuture.completedFuture(null);
        for (HttpRequest request : requests) {
            delivery = delivery.thenCompose(ignored -> HttpRequests.sendAsync(request)
                    .thenAccept(response -> this.logRejectedResponse(request, response))
                    .exceptionally(failure -> {
                        ClientAccess.logger.warn("Notification delivery failed ({})", describeFailure(failure));
                        return null;
                    }));
        }
    }

    private void logRejectedResponse(HttpRequest request, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            ClientAccess.logger.warn("Notification destination rejected the message (HTTP {})", status);
            return;
        }
        String host = request.uri().getHost();
        if (host == null) {
            return;
        }
        if ("api.telegram.org".equalsIgnoreCase(host)) {
            try {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!result.has("ok") || !result.get("ok").getAsBoolean()) {
                    ClientAccess.logger.warn("Telegram rejected the notification");
                }
            } catch (RuntimeException invalidResponse) {
                ClientAccess.logger.warn("Telegram returned an invalid notification response");
            }
        } else if (host.endsWith("sgroup.qq.com")) {
            try {
                JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
                if (result.has("code") && !result.get("code").isJsonNull()) {
                    ClientAccess.logger.warn("QQ bot rejected the notification (code {})", result.get("code").getAsString());
                }
            } catch (RuntimeException ignoredFailure) {
            }
        }
    }

    CompletableFuture<String> requestQqAccessToken() {
        synchronized (this) {
            String cached = this.qqAccessToken;
            if (cached != null && System.currentTimeMillis() < this.qqAccessTokenExpiresAt) {
                return CompletableFuture.completedFuture(cached);
            }
            CompletableFuture<String> pending = this.qqAccessTokenRequest;
            if (pending != null && !pending.isDone()) {
                return pending;
            }
            String appId = this.qqAppId;
            String clientSecret = this.qqClientSecret;
            if (appId.isBlank() || clientSecret.isBlank()) {
                return CompletableFuture.failedFuture(new IllegalStateException("QQ bot credentials are missing"));
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("appId", appId);
            payload.addProperty("clientSecret", clientSecret);
            CompletableFuture<String> refresh = this.requestQqAccessToken(0, payload.toString());
            this.qqAccessTokenRequest = refresh;
            return refresh;
        }
    }

    private CompletableFuture<String> requestQqAccessToken(int endpointIndex, String payload) {
        if (endpointIndex >= QQ_TOKEN_ENDPOINTS.size()) {
            return CompletableFuture.failedFuture(new IllegalStateException("QQ bot token endpoint unavailable"));
        }
        HttpRequest request = jsonRequest(QQ_TOKEN_ENDPOINTS.get(endpointIndex), payload).build();
        return HttpRequests.sendAsync(request).thenCompose(response -> {
            String token = this.parseQqAccessToken(response);
            if (token != null) {
                return CompletableFuture.completedFuture(token);
            }
            return this.requestQqAccessToken(endpointIndex + 1, payload);
        });
    }

    private String parseQqAccessToken(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement tokenElement = json.get("access_token");
            if (tokenElement == null || tokenElement.isJsonNull()) {
                JsonElement code = json.get("code");
                ClientAccess.logger.warn("QQ bot token request failed (code {})",
                        code == null || code.isJsonNull() ? "unknown" : code.getAsString());
                return null;
            }
            String token = tokenElement.getAsString();
            if (token.isBlank()) {
                return null;
            }
            long expiresInSeconds = 7200L;
            JsonElement expiresElement = json.get("expires_in");
            if (expiresElement != null && !expiresElement.isJsonNull()) {
                try {
                    expiresInSeconds = Long.parseLong(expiresElement.getAsString().trim());
                } catch (RuntimeException invalidExpiry) {
                    expiresInSeconds = 7200L;
                }
            }
            long lifetimeMillis = Math.max(60L, expiresInSeconds * 1000L - QQ_TOKEN_REFRESH_MARGIN_MILLIS);
            this.qqAccessToken = token;
            this.qqAccessTokenExpiresAt = System.currentTimeMillis() + lifetimeMillis;
            return token;
        } catch (RuntimeException invalidResponse) {
            return null;
        }
    }

    void invalidateQqAccessToken() {
        synchronized (this) {
            this.qqAccessToken = null;
            this.qqAccessTokenExpiresAt = 0L;
            this.qqAccessTokenRequest = null;
        }
    }

    /**
     * Telegram bot credentials extracted from a legacy {@code .../bot<token>/sendMessage?chat_id=...} URL.
     */
    public record TelegramEndpoint(String botToken, String chatId) {
    }
}
