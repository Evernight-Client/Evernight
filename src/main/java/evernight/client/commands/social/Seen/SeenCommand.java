package evernight.client.commands.social.Seen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import evernight.client.commands.misc.TwoB2tApi.TwoB2tApiCommand;
import evernight.client.commands.Command;

public class SeenCommand extends TwoB2tApiCommand {
    private final DateTimeFormatter lastSeenFormatter;

    public SeenCommand() {
        super("seen");
        Locale englishLocale = Locale.ENGLISH;
        this.lastSeenFormatter = DateTimeFormatter.ofPattern(("dd MMM yyyy HH:mm:ss"), englishLocale);
        this.setAliases("lastseen");
    }

    @Override
    public void exec(LiteralArgumentBuilder commandBuilder) {
        StringArgumentType nameArgumentType = StringArgumentType.greedyString();
        RequiredArgumentBuilder nameBuilder = Command.argument("name", nameArgumentType);
        com.mojang.brigadier.Command<Object> executor = commandContext -> {
            String playerName = commandContext.getArgument("name", String.class);
            Component fetchingMessage = Component.nullToEmpty(Translations.ui("commands.Seen.Fetching"));
            SeenCommand fetchingCommand = this;
            MessageSignature fetchingSignature = ChatUtils.objectSignature(fetchingCommand);
            ChatUtils.printChat(fetchingMessage, fetchingSignature);
            String endpoint = "seen";
            SeenCommand requestCommand = this;
            CompletableFuture<JsonObject> request = requestCommand.fetchData(endpoint, playerName);
            BiConsumer<JsonObject, Throwable> completion = (seenData, requestFailure) -> {
                Object errorDetail = null;
                if (requestFailure != null) {
                    Throwable failedRequest = requestFailure;
                    errorDetail = failedRequest.getMessage();
                } else {
                    try {
                        String lastSeenPresenceKey = "lastSeen";
                        JsonObject lastSeenPresenceData = seenData;
                        if (lastSeenPresenceData.has(lastSeenPresenceKey)) {
                            String lastSeenNullKey = "lastSeen";
                            JsonObject lastSeenNullData = seenData;
                            JsonElement lastSeenNullableValue = lastSeenNullData.get(lastSeenNullKey);
                            if (lastSeenNullableValue.isJsonNull()) {
                                throw new RuntimeException(Translations.ui("commands.Seen.NoData"));
                            }
                            String lastSeenValueKey = "lastSeen";
                            JsonObject lastSeenValueData = seenData;
                            JsonElement lastSeenValue = lastSeenValueData.get(lastSeenValueKey);
                            String lastSeenText = lastSeenValue.getAsString();
                            OffsetDateTime lastSeenTime = OffsetDateTime.parse(lastSeenText);
                            ZoneOffset utc = ZoneOffset.UTC;
                            OffsetDateTime currentTime = OffsetDateTime.now(utc);
                            Duration timeSinceLastSeen = Duration.between(lastSeenTime, currentTime);
                            StringBuilder seenSummary = new StringBuilder(playerName);
                            int elapsedSeconds = (int) (timeSinceLastSeen).toSeconds();
                            if (elapsedSeconds == 0) {
                                seenSummary.append(Translations.ui("commands.Seen.OnlineNow"));
                            } else {
                                seenSummary.append(Translations.ui("commands.Seen.WasSeenPrefix"));
                                int durationSeconds = (int) (timeSinceLastSeen).toSeconds();
                                String durationText = TwoB2tApiCommand.formatDuration((durationSeconds));
                                seenSummary.append(durationText);
                                seenSummary.append(Translations.ui("commands.Seen.WasSeenAgo"));
                                String formattedLastSeen = this.lastSeenFormatter.format(lastSeenTime);
                                seenSummary.append(formattedLastSeen);
                                seenSummary.append(Translations.ui("commands.Seen.WasSeenSuffix"));
                            }
                            String summaryText = seenSummary.toString();
                            Component summaryMessage = Component.nullToEmpty(summaryText);
                            SeenCommand completedCommand = this;
                            MessageSignature summarySignature = ChatUtils.objectSignature(completedCommand);
                            ChatUtils.printChat(summaryMessage, summarySignature);
                            return;
                        }
                        errorDetail = Translations.format("commands.Seen.MissingField", "lastSeen");
                    } catch (Exception responseFailure) {
                        errorDetail = responseFailure.getMessage();
                    }
                }
                if (errorDetail != null) {
                    ChatFormatting errorColor = ChatFormatting.RED;
                    String errorColorText = String.valueOf(errorColor);
                    String errorText = errorColorText + errorDetail;
                    Component errorMessage = Component.nullToEmpty(errorText);
                    SeenCommand failedCommand = this;
                    MessageSignature errorSignature = ChatUtils.objectSignature(failedCommand);
                    ChatUtils.MessagePriority errorPriority = ChatUtils.MessagePriority.MID;
                    ChatUtils.printChat(errorMessage, errorSignature, errorPriority);
                }
            };
            request.whenComplete(completion);
            return 1;
        };
        ArgumentBuilder nameArgument = nameBuilder.executes(executor);
        commandBuilder.then(nameArgument);
    }
}