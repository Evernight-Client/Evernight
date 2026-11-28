package evernight.client.commands.misc.TwoB2tApi;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.EvernightClient;
import evernight.client.render.text.Translations;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import evernight.client.commands.Command;
import evernight.client.utils.network.HttpRequests;

public class TwoB2tApiCommand extends Command {
    private static final Pattern USERNAME_PATTERN;

    static {
        String usernamePattern = "[a-zA-Z0-9_]{1,16}";
        USERNAME_PATTERN = Pattern.compile(usernamePattern);
    }

    public TwoB2tApiCommand(String commandName) {
        super(commandName);
    }

    public static String formatDuration(int totalSeconds) {
        StringBuilder duration = new StringBuilder();
        int days = totalSeconds / 86400;
        int hours = totalSeconds / 3600 % 24;
        int minutes = totalSeconds / 60 % 60;
        int seconds = totalSeconds % 60;
        if (days > 0) {
            duration.append(Translations.format("commands.TwoB2tApi.Day", days, pluralSuffix(days)));
        }
        if (days > 0 || hours > 0) {
            duration.append(Translations.format("commands.TwoB2tApi.Hour", hours, pluralSuffix(hours)));
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            duration.append(Translations.format("commands.TwoB2tApi.Minute", minutes, pluralSuffix(minutes)));
        }
        duration.append(Translations.format("commands.TwoB2tApi.Second", seconds, pluralSuffix(seconds)));
        return duration.toString();
    }

    public static String pluralSuffix(int count) {
        return count == 1 ? "" : "s";
    }

    @Override
    public void exec(LiteralArgumentBuilder rootArgument) {
    }

    public CompletableFuture<JsonObject> fetchData(String endpoint, String playerName) {
        if (playerName != null && !playerName.isEmpty()
                && (playerName.length() < 3 || !USERNAME_PATTERN.matcher(playerName).matches())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(Translations.ui("commands.TwoB2tApi.InvalidUsername")));
        }
        String path = endpoint + (playerName == null || playerName.isEmpty() ? "" : "?playerName=" + playerName);
        return CompletableFuture.supplyAsync(() -> {
            RuntimeException lastFailure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpResponse<String> response;
                try {
                    var request = evernight.client.utils.network.HttpRequests.get("https://api.2b2t.vc/" + path)
                            .header("User-Agent", EvernightClient.USER_AGENT).header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(10)).build();
                    response = evernight.client.utils.network.HttpRequests.send(request);
                } catch (RuntimeException failure) {
                    lastFailure = failure;
                    if (Thread.currentThread().isInterrupted()) throw failure;
                    if (attempt == 0) {
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new java.util.concurrent.CompletionException(interrupted);
                        }
                    }
                    continue;
                }
                return switch (response.statusCode()) {
                    case 200 -> evernight.client.ClientAccess.gson.fromJson(response.body(), JsonObject.class);
                    case 204 -> throw new IllegalStateException(playerName == null || playerName.isEmpty()
                            ? Translations.ui("commands.TwoB2tApi.NoData")
                            : Translations.ui("commands.TwoB2tApi.NoDataForPlayer"));
                    case 429 ->
                            throw new IllegalStateException(Translations.ui("commands.TwoB2tApi.RateLimited"));
                    default ->
                            throw new IllegalStateException(Translations.format("commands.TwoB2tApi.InvalidResponseCode", response.statusCode()));
                };
            }
            throw new java.util.concurrent.CompletionException(Translations.ui("commands.TwoB2tApi.FetchFailed"), lastFailure);
        });
    }
}