package evernight.client.commands.social.Namehistory;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientContext;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.network.HttpRequests;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpRequest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import evernight.client.commands.arguments.PlayerNameArgumentType;
import evernight.client.commands.Command;

public final class NamehistoryCommand extends Command {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private CompletableFuture<List<NameHistoryEntry>> pendingHistory;

    public NamehistoryCommand() {
        super("namehistory");
    }

    @Override
    public void exec(LiteralArgumentBuilder builder) {
        ArgumentBuilder historyArgument = Command.argument("name", new PlayerNameArgumentType())
                .executes((com.mojang.brigadier.Command<Object>) context -> {
                    if (this.pendingHistory != null) {
                        CompletableFuture<List<NameHistoryEntry>> runningHistory = this.pendingHistory;
                        if (!runningHistory.isDone()) {
                            CompletableFuture<List<NameHistoryEntry>> failedHistory = this.pendingHistory;
                            if (!failedHistory.isCompletedExceptionally()) {
                                return 1;
                            }
                        }
                    }
                    String playerName = context.getArgument("name", String.class);
                    this.pendingHistory = this.lookupNameHistory(playerName);
                    this.pendingHistory.thenAccept(result -> {
                        TaskScheduler scheduler = ClientContext.taskScheduler;
                        Runnable task = () -> this.showNameHistory(result);
                        scheduler.scheduleRotationTask(task, 0);
                    });
                    this.pendingHistory.exceptionally(lookupFailure -> {
                                TaskScheduler scheduler = ClientContext.taskScheduler;
                                Runnable task = () -> {
                                    Throwable capturedFailure = lookupFailure;
                                    NamehistoryCommand failureCommand = this;
                                    failureCommand.showLookupFailure(capturedFailure);
                                };
                                scheduler.scheduleRotationTask(task, 0);
                                return null;
                            }
                    );
                    MutableComponent loadingMessage = Component.literal(Translations.ui("commands.Namehistory.LoadingPrefix"));
                    NamehistoryCommand formatCommand = this;
                    MutableComponent formattedLoadingMessage = loadingMessage.append(formatCommand.formatText(playerName));
                    ChatUtils.printChat(formattedLoadingMessage, ChatUtils.createSignature(-3));
                    return 1;
                });
        builder.then(historyArgument);
        LiteralArgumentBuilder openBuilder = Command.literal("open");
        ArgumentBuilder openArgument = Command.argument("name", new PlayerNameArgumentType())
                .executes((com.mojang.brigadier.Command<Object>) openContext -> {
                    try {
                        URI profileUri = new URI(
                                "https://namemc.com/profile/" + openContext.getArgument("name", String.class)
                        );
                        Util.getPlatform().openUri(profileUri);
                    } catch (Throwable ignoredBrowserFailure) {
                    }
                    return 1;
                });
        ArgumentBuilder openBranch = openBuilder.then(openArgument);
        builder.then(openBranch);
    }

    private void showLookupFailure(Throwable failure) {
        Throwable cause = failure.getCause();
        String message = cause.getMessage();
        MutableComponent errorMessage = Component.literal(message);
        ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-3), ChatUtils.MessagePriority.HIGH);
    }

    private List<NameHistoryEntry> parseNameHistory(String responseBody) {
        JsonElement parsedJson = JsonParser.parseString(responseBody);
        if (!parsedJson.isJsonArray()) {
            return null;
        }
        JsonArray entries = parsedJson.getAsJsonArray();
        Stream<NameHistoryEntry> parsedEntries = entries.asList().stream().map(entryJson -> {
            Class<NamehistoryCommand.NameHistoryEntry> entryType = NamehistoryCommand.NameHistoryEntry.class;
            JsonElement capturedEntry = entryJson;
            Gson gson = evernight.client.ClientAccess.gson;
            return gson.fromJson(capturedEntry, entryType);
        });
        return parsedEntries.collect(Collectors.toList());
    }

    private CompletableFuture<List<NameHistoryEntry>> lookupNameHistory(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String profileUrl = "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + playerName;
            HttpRequest profileRequest = HttpRequests.get(profileUrl).build();
            String profileBody = HttpRequests.send(profileRequest).body();
            JsonElement parsedProfile = JsonParser.parseString(profileBody);
            if (parsedProfile.isJsonObject()) {
                JsonObject profileObject = parsedProfile.getAsJsonObject();
                if (profileObject.has("id")) {
                    JsonObject profileWithIdObject = parsedProfile.getAsJsonObject();
                    JsonElement idElement = profileWithIdObject.get("id");
                    String profileId = idElement.getAsString();
                    Object[] historyUrlArguments = new Object[]{profileId};
                    String historyUrl = "https://laby.net/api/user/%s/get-names".formatted(historyUrlArguments);
                    HttpRequest historyRequest = HttpRequests.get(historyUrl).build();
                    String historyBody = HttpRequests.send(historyRequest).body();
                    return this.parseNameHistory(historyBody);
                }
            }
            throw new RuntimeException(Translations.ui("commands.Namehistory.PlayerNotFound"));
        }, evernight.client.ClientAccess.executorService);
    }

    private void showNameHistory(List<NameHistoryEntry> history) {
        if (history != null) {
            try {
                MutableComponent message = Component.empty();
                NamehistoryCommand.NameHistoryEntry lastEntry = history.getLast();
                String lastUsername = lastEntry.displayUsername();
                NamehistoryCommand formatCommand = this;
                message.append(formatCommand.formatText(lastUsername));
                message.append(Translations.ui("commands.Namehistory.HistoryHeader"));
                StringBuilder lines = new StringBuilder();
                for (int index = 0; index < history.size(); index++) {
                    NamehistoryCommand.NameHistoryEntry entry = history.get(index);
                    Object[] lineArguments = new Object[3];
                    int lineNumber = index + 1;
                    lineArguments[0] = lineNumber;
                    lineArguments[1] = entry.displayUsername();
                    lineArguments[(2)] = entry.formatChangeDate();
                    String line = "%d. %s (%s)".formatted(lineArguments);
                    lines.append(line);
                    if (index != history.size() - 1) {
                        lines.append('\n');
                    }
                }
                String historyText = lines.toString();
                message.append(historyText);
                ChatUtils.printChat(message, ChatUtils.createSignature(-3));
            } catch (Exception formatFailure) {
                LOGGER.warn("Could not format name history", formatFailure);
            }
        }
    }

    private record NameHistoryEntry(@SerializedName("username") String username,
                                    @SerializedName("changed_at") String changedAt,
                                    @SerializedName("hidden") boolean hidden) {
            private static final SimpleDateFormat CHANGE_DATE_FORMAT;

            static {
                CHANGE_DATE_FORMAT = new SimpleDateFormat("d MMM yyyy HH:mm:ss");
            }

        public String formatChangeDate() {
                if (this.changedAt == null) {
                    return Translations.ui("commands.Namehistory.FirstName");
                }
                String localTimestamp = this.changedAt.split("\\+")[0];
                try {
                    LocalDateTime parsedTime = LocalDateTime.parse(localTimestamp);
                    SimpleDateFormat dateFormat = CHANGE_DATE_FORMAT;
                    ZoneId localZone = ZoneId.systemDefault();
                    Instant instant = parsedTime.atZone(localZone).toInstant();
                    Date date = Date.from(instant);
                    return dateFormat.format(date);
                } catch (Exception parseFailure) {
                    return localTimestamp;
                }
            }

            public String displayUsername() {
                if (this.hidden) {
                    return Translations.ui("commands.Namehistory.Hidden");
                } else {
                    return this.username;
                }
            }

            @Override
            public String toString() {
                return "NameHistoryEntry[username=" + this.username
                        + ", changedAt=" + this.changedAt
                        + ", hidden=" + this.hidden + "]";
            }

            @Override
            public int hashCode() {
                return 31 * (31 * Objects.hashCode(this.username) + Objects.hashCode(this.changedAt)) + Boolean.hashCode(this.hidden);
            }

            @Override
            public boolean equals(Object other) {
                return this == other || other instanceof NameHistoryEntry entry && this.hidden == entry.hidden
                        && Objects.equals(this.changedAt, entry.changedAt)
                        && Objects.equals(this.username, entry.username);
            }
        }
}