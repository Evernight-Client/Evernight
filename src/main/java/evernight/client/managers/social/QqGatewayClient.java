package evernight.client.managers.social;

import com.google.gson.*;
import evernight.client.ClientAccess;
import evernight.client.EvernightClient;
import evernight.client.utils.network.HttpRequests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Minimal QQ Bot OpenAPI gateway listener.
 *
 * <p>It only exists to bind the single-chat notification target: the OpenAPI cannot enumerate the
 * users that ever talked to the bot, so while {@code openId} in
 * {@link evernight.client.managers.social.WebhookManager} is empty the first
 * {@code C2C_MESSAGE_CREATE} event received over the gateway is adopted as the destination.
 *
 * <p>The connection follows the documented handshake: {@code GET /gateway}, {@code HELLO} (op 10),
 * {@code IDENTIFY} (op 2) with the {@code GROUP_AND_C2C_EVENT} intent, then periodic heartbeat
 * (op 1) with the latest sequence. Reconnects use capped exponential backoff and stop as soon as
 * {@link #stop()} is called.
 */
public final class QqGatewayClient {
    private static final URI GATEWAY_ENDPOINT = URI.create("https://api.sgroup.qq.com/gateway");
    private static final int GROUP_AND_C2C_INTENT = 1 << 25;
    private static final long DEFAULT_HEARTBEAT_MILLIS = 41_250L;
    private static final long BASE_RECONNECT_MILLIS = 5_000L;
    private static final long MAX_RECONNECT_MILLIS = 60_000L;

    private final WebhookManager manager;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private volatile ScheduledExecutorService scheduler;
    private volatile WebSocket socket;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile boolean running;
    private volatile int sequence;
    private volatile long heartbeatIntervalMillis = DEFAULT_HEARTBEAT_MILLIS;
    private volatile int reconnectAttempt;
    private volatile String accessToken = "";
    private volatile boolean failureLogged;

    QqGatewayClient(WebhookManager manager) {
        this.manager = manager;
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString().trim();
        } catch (RuntimeException invalidValue) {
            return "";
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    /**
     * Starts the listener once; repeated calls while running are ignored.
     */
    public void start() {
        synchronized (this) {
            if (this.running) {
                return;
            }
            this.running = true;
            this.reconnectAttempt = 0;
            this.sequence = 0;
            this.failureLogged = false;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Evernight-QQ-Gateway");
                thread.setDaemon(true);
                return thread;
            });
        }
        this.connect();
    }

    /**
     * Stops the listener and closes the socket; safe to call when not running.
     */
    public void stop() {
        ScheduledExecutorService scheduler;
        synchronized (this) {
            if (!this.running) {
                return;
            }
            this.running = false;
            scheduler = this.scheduler;
            this.scheduler = null;
        }
        this.stopHeartbeat();
        WebSocket current = this.socket;
        this.socket = null;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "stop");
            } catch (RuntimeException alreadyClosed) {
                current.abort();
            }
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void connect() {
        if (!this.running) {
            return;
        }
        this.manager.requestQqAccessToken().thenCompose(token -> {
            this.accessToken = token;
            HttpRequest request = HttpRequest.newBuilder(GATEWAY_ENDPOINT).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "QQBot " + token)
                    .header("User-Agent", EvernightClient.USER_AGENT)
                    .GET().build();
            return HttpRequests.sendAsync(request);
        }).thenAccept(this::openSocket).exceptionally(failure -> {
            this.logFailure("gateway handshake failed", failure);
            this.scheduleReconnect();
            return null;
        });
    }

    private void openSocket(HttpResponse<String> response) {
        if (!this.running) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            this.logFailure("gateway endpoint rejected the request (HTTP " + response.statusCode() + ")", null);
            this.scheduleReconnect();
            return;
        }
        String url;
        try {
            url = JsonParser.parseString(response.body()).getAsJsonObject().get("url").getAsString();
        } catch (RuntimeException invalidResponse) {
            this.logFailure("gateway endpoint returned an invalid response", invalidResponse);
            this.scheduleReconnect();
            return;
        }
        this.httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(url), new GatewayListener())
                .whenComplete((webSocket, failure) -> {
                    if (!this.running) {
                        if (webSocket != null) {
                            webSocket.abort();
                        }
                        return;
                    }
                    if (failure != null) {
                        this.logFailure("gateway WebSocket connect failed", failure);
                        this.scheduleReconnect();
                        return;
                    }
                    this.socket = webSocket;
                });
    }

    private void handlePayload(String payload) {
        JsonObject root;
        try {
            root = JsonParser.parseString(payload).getAsJsonObject();
        } catch (RuntimeException invalidPayload) {
            return;
        }
        JsonElement sequenceElement = root.get("s");
        if (sequenceElement != null && !sequenceElement.isJsonNull()) {
            try {
                this.sequence = sequenceElement.getAsInt();
            } catch (RuntimeException ignoredFailure) {
            }
        }
        int operation = root.has("op") ? root.get("op").getAsInt() : -1;
        switch (operation) {
            case 0 -> this.handleDispatch(root);
            case 7 -> this.reconnect("gateway requested a reconnect");
            case 9 -> {
                this.manager.invalidateQqAccessToken();
                this.reconnect("gateway session was invalidated");
            }
            case 10 -> this.handleHello(root);
            default -> {
            }
        }
    }

    private void handleHello(JsonObject root) {
        long interval = DEFAULT_HEARTBEAT_MILLIS;
        if (root.get("d") instanceof JsonObject hello && hello.has("heartbeat_interval")) {
            try {
                interval = hello.get("heartbeat_interval").getAsLong();
            } catch (RuntimeException invalidInterval) {
                interval = DEFAULT_HEARTBEAT_MILLIS;
            }
        }
        this.heartbeatIntervalMillis = Math.max(BASE_RECONNECT_MILLIS, interval);
        this.sendIdentify();
        this.startHeartbeat();
    }

    private void sendIdentify() {
        JsonObject payload = new JsonObject();
        payload.addProperty("token", "QQBot " + this.accessToken);
        payload.addProperty("intents", GROUP_AND_C2C_INTENT);
        JsonArray shard = new JsonArray();
        shard.add(0);
        shard.add(1);
        JsonObject identify = new JsonObject();
        identify.add("shard", shard);
        JsonObject properties = new JsonObject();
        properties.addProperty("$os", System.getProperty("os.name", "linux"));
        properties.addProperty("$browser", "Evernight");
        properties.addProperty("$device", "Evernight");
        identify.add("properties", properties);
        payload.add("d", identify);
        this.send(2, payload);
    }

    private void handleDispatch(JsonObject root) {
        String type = root.has("t") && !root.get("t").isJsonNull() ? root.get("t").getAsString() : "";
        if (root.get("d") instanceof JsonObject data) {
            if ("C2C_MESSAGE_CREATE".equals(type)) {
                this.bindAuthor(data);
            } else if ("READY".equals(type) || "RESUMED".equals(type)) {
                this.reconnectAttempt = 0;
                this.failureLogged = false;
                ClientAccess.logger.debug("QQ gateway ready (event {})", type);
            }
        }
    }

    private void bindAuthor(JsonObject data) {
        String openId = "";
        if (data.get("author") instanceof JsonObject author) {
            openId = readString(author, "user_openid");
            if (openId.isBlank()) {
                openId = readString(author, "id");
            }
        }
        if (!openId.isBlank()) {
            this.manager.bindQqOpenId(openId);
        }
    }

    private void startHeartbeat() {
        this.stopHeartbeat();
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }
        long interval = this.heartbeatIntervalMillis;
        try {
            this.heartbeat = scheduler.scheduleAtFixedRate(this::sendHeartbeat, interval, interval,
                    TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
        }
    }

    private void sendHeartbeat() {
        JsonObject payload = new JsonObject();
        if (this.sequence > 0) {
            payload.addProperty("d", this.sequence);
        } else {
            payload.add("d", JsonNull.INSTANCE);
        }
        this.send(1, payload);
    }

    private void send(int operation, JsonObject payload) {
        WebSocket current = this.socket;
        if (current == null) {
            return;
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("op", operation);
        envelope.add("d", payload);
        try {
            current.sendText(envelope.toString(), true).exceptionally(failure -> null);
        } catch (RuntimeException closed) {
            // The close listener schedules the reconnect.
        }
    }

    private void reconnect(String reason) {
        if (!this.running) {
            return;
        }
        ClientAccess.logger.debug("QQ gateway reconnect: {}", reason);
        this.sequence = 0;
        this.closeSocket();
        this.scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!this.running) {
            return;
        }
        ScheduledExecutorService scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }
        this.reconnectAttempt++;
        long delay = Math.min(MAX_RECONNECT_MILLIS, BASE_RECONNECT_MILLIS * this.reconnectAttempt);
        try {
            scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
        }
    }

    private void closeSocket() {
        this.stopHeartbeat();
        WebSocket current = this.socket;
        this.socket = null;
        if (current == null) {
            return;
        }
        try {
            current.abort();
        } catch (RuntimeException ignoredFailure) {
        }
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> current = this.heartbeat;
        this.heartbeat = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void logFailure(String message, Throwable failure) {
        if (this.failureLogged) {
            if (failure != null) {
                ClientAccess.logger.debug("QQ gateway retry ({})", failure.getClass().getSimpleName());
            }
            return;
        }
        this.failureLogged = true;
        if (failure != null) {
            ClientAccess.logger.warn("{} ({})", message, failure.getClass().getSimpleName());
        } else {
            ClientAccess.logger.warn(message);
        }
    }

    private final class GatewayListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.buffer.append(data);
            if (last) {
                String payload = this.buffer.toString();
                this.buffer.setLength(0);
                QqGatewayClient.this.handlePayload(payload);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (QqGatewayClient.this.socket == webSocket) {
                QqGatewayClient.this.socket = null;
            }
            QqGatewayClient.this.stopHeartbeat();
            if (QqGatewayClient.this.running) {
                ClientAccess.logger.debug("QQ gateway closed ({}), reconnecting", statusCode);
                QqGatewayClient.this.scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable failure) {
            if (QqGatewayClient.this.socket == webSocket) {
                QqGatewayClient.this.socket = null;
            }
            QqGatewayClient.this.stopHeartbeat();
            if (QqGatewayClient.this.running) {
                QqGatewayClient.this.logFailure("QQ gateway socket error", failure);
                QqGatewayClient.this.scheduleReconnect();
            }
        }
    }
}
