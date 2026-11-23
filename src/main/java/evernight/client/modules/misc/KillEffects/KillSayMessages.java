package evernight.client.modules.misc.KillEffects;

import evernight.client.ClientContext;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

/**
 * Owns the kill say lines.
 *
 * <p>The killsay text file remains the source of truth: {@link #reloadMessages(boolean)} reads it and
 * the in-game editor writes every change straight back, so messages can be maintained without a
 * command or an offline editor. A synchronized copy is kept for the tick thread.
 */
public final class KillSayMessages {
    private final KillEffectsModule module;
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private int lineIndex = 0;
    private boolean loading;

    public KillSayMessages(KillEffectsModule module) {
        this.module = module;
    }

    private String nextMessage() {
        try {
            synchronized (this.lines) {
                if (this.lines.isEmpty()) {
                    return null;
                }
                Setting<Boolean> randomOption = this.module.randomSetting;
                Boolean random = randomOption.getValue();
                if (random) {
                    int lineCount = this.lines.size();
                    return this.lines.get(ThreadLocalRandom.current().nextInt(lineCount));
                } else {
                    return this.lines.get(this.lineIndex++ % this.lines.size());
                }
            }
        } catch (Exception selectionFailure) {
            return null;
        }
    }

    void resetSequence() {
        this.lineIndex = 0;
    }

    void sendKillMessage(Player player, String playerName) {
        Setting<Boolean> killSayOption = this.module.killSaySetting;
        Boolean killSay = killSayOption.getValue();
        if (!killSay) {
            return;
        }
        Setting<Boolean> onlyEnemiesOption = this.module.onlyEnemiesSetting;
        boolean onlyEnemies = onlyEnemiesOption.getValue();
        if (onlyEnemies) {
            SocialRegistry enemyManagerSnapshot = ClientContext.socialRegistry;
            if (!enemyManagerSnapshot.isEnemyPlayer(player)) {
                return;
            }
        }
        String message = this.nextMessage();
        if (message == null) {
            return;
        }
        String messageToSend = message.replace("{name}", playerName);
        Runnable sendMessage = () -> ChatUtils.sendChatOrCommand(messageToSend);
        Setting<Float> delayOptionSnapshot = this.module.delaySetting;
        if (delayOptionSnapshot.isAtMinimum()) {
            sendMessage.run();
            return;
        }
        TaskScheduler scheduler = ClientContext.taskScheduler;
        float delaySeconds = delayOptionSnapshot.getValue();
        int delayTicks = (int) (delaySeconds / 0.05F);
        scheduler.scheduleRotationTask(sendMessage, delayTicks);
    }

    private Path resolveExistingFile() {
        Path resolvedPath = ConfigurationManager.KILLSAY_DIRECTORY.resolve(this.module.pathSetting.getValue());
        return evernight.client.utils.core.FileUtils.findExistingWithSuffixes(resolvedPath, ".txt");
    }

    private Path resolveWritableFile() {
        Path existingPath = this.resolveExistingFile();
        if (existingPath.toFile().exists()) {
            return existingPath;
        }
        Path resolvedPath = ConfigurationManager.KILLSAY_DIRECTORY.resolve(this.module.pathSetting.getValue());
        String fileName = resolvedPath.getFileName().toString();
        return fileName.endsWith(".txt") ? resolvedPath : resolvedPath.resolveSibling(fileName + ".txt");
    }

    /** Mirrors the editor list into the synchronized copy used while sending messages. */
    void syncFromSetting() {
        List<String> entries = new ArrayList<>(this.module.killSayMessagesSetting.getValue());
        synchronized (this.lines) {
            this.lines.clear();
            for (String entry : entries) {
                if (entry != null && !entry.isBlank()) {
                    this.lines.add(entry);
                }
            }
        }
        this.lineIndex = 0;
    }

    /** Called whenever the in-game list changes: refresh the sending copy and persist the file. */
    void onSettingChanged() {
        this.syncFromSetting();
        if (!this.loading) {
            this.saveToFile();
        }
    }

    /**
     * Reloads the lines from the killsay text file into the editor list.
     *
     * @param notifyFailure whether a missing/unreadable file should be reported in chat
     */
    void reloadMessages(boolean notifyFailure) {
        this.loading = true;
        try {
            Path path = this.resolveExistingFile();
            if (!path.toFile().exists()) {
                throw new IOException("KillSay file %s not found".formatted(this.module.pathSetting.getValue()));
            }
            List<String> fileLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            this.module.killSayMessagesSetting.replaceEntries(fileLines);
        } catch (Exception loadFailure) {
            if (notifyFailure && this.module.killSaySetting.getValue()) {
                String failureMessage = Translations.ui("modules.KillEffects.FailedToUpdateKillSay") + loadFailure;
                MutableComponent notification = Component.literal(failureMessage);
                UnaryOperator<Style> notificationStyler = style -> style.withColor(ChatFormatting.RED);
                notification = notification.withStyle(notificationStyler);
                MessageSignature signature = ChatUtils.createSignature(-9634482);
                ChatUtils.printChat(notification, signature, ChatUtils.MessagePriority.LOW);
                try {
                    Path path = this.resolveWritableFile();
                    Files.createDirectories(path.getParent());
                    Files.createFile(path);
                } catch (IOException creationFailure) {
                }
            }
        } finally {
            this.loading = false;
        }
        this.syncFromSetting();
    }

    private void saveToFile() {
        try {
            Path path = this.resolveWritableFile();
            Files.createDirectories(path.getParent());
            Files.write(path, new ArrayList<>(this.module.killSayMessagesSetting.getValue()),
                    StandardCharsets.UTF_8);
        } catch (Exception ignoredSaveFailure) {
        }
    }
}
