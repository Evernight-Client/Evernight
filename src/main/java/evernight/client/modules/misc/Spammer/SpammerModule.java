package evernight.client.modules.misc.Spammer;

import evernight.client.events.core.EventHandler;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.StringListSetting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.core.ElapsedTimer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Sends the lines of a text file in sequence.
 *
 * <p>The file remains the source of truth, so the list can still be edited from outside the game.
 * The {@code Messages} row in the ClickGUI opens an in-game editor for the same file: every edit is
 * written back immediately, so neither a command nor an offline editor is required anymore.
 */
public class SpammerModule extends Module {
    private final Setting<String> pathSetting;
    private final StringListSetting messagesSetting;
    private final Setting<Float> delaySetting;
    private final Setting<Boolean> refreshSetting;
    private final Setting<Boolean> smartDelaySetting;
    private final Setting<Boolean> randomSetting;
    private final Setting<Boolean> autoDisableSetting;
    private final Random messageRandom;
    private final ElapsedTimer sendTimer;
    private int nextMessageIndex;
    private boolean loadingMessages;

    public SpammerModule() {
        super("Spammer", ModuleCategory.MISC);
        this.pathSetting = this.add(new StringSetting("Path", "spammer.txt"));
        this.messagesSetting = this.add(new StringListSetting("Messages"));
        // The text file stays the single source of truth, so the in-memory list is not persisted.
        this.messagesSetting.setExcludedFromConfig(true);
        this.delaySetting = this.add(new NumberSetting<>("Delay", 5.0F, 0.0F, 300.0F).withUnit("s"));
        this.refreshSetting = this.add(new BooleanSetting("Refresh", false));
        this.smartDelaySetting = this.add(new BooleanSetting("SmartDelay", true));
        this.randomSetting = this.add(new BooleanSetting("Random", false));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
        this.messageRandom = new Random();
        this.sendTimer = new ElapsedTimer();
        this.nextMessageIndex = 0;
        this.loadingMessages = false;
        this.messagesSetting.setChangeCallback(this::onMessagesChanged);
        // Reload the file right before the editor opens, so it never edits a stale snapshot.
        this.messagesSetting.withPrepareAction(() -> this.loadMessages(false));
        this.pathSetting.setChangeCallback(() -> this.loadMessages(true));
        this.refreshSetting.setChangeCallback(() -> {
            if (this.refreshSetting.getValue()) {
                this.refreshSetting.setValue(false);
                this.loadMessages(true);
            }
        });
    }

    private Path resolveExistingFile() {
        Path resolvedPath = ConfigurationManager.SPAMMER_DIRECTORY.resolve(this.pathSetting.getValue());
        return evernight.client.utils.core.FileUtils.findExistingWithSuffixes(resolvedPath, ".txt");
    }

    private Path resolveWritableFile() {
        Path existingPath = this.resolveExistingFile();
        if (existingPath.toFile().exists()) {
            return existingPath;
        }
        Path resolvedPath = ConfigurationManager.SPAMMER_DIRECTORY.resolve(this.pathSetting.getValue());
        String fileName = resolvedPath.getFileName().toString();
        return fileName.endsWith(".txt") ? resolvedPath : resolvedPath.resolveSibling(fileName + ".txt");
    }

    private void loadMessages(boolean notifyFailure) {
        try {
            Path filePath = this.resolveExistingFile();
            if (!filePath.toFile().exists()) {
                throw new RuntimeException("Invalid spammer file path");
            }
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            this.loadingMessages = true;
            try {
                this.messagesSetting.replaceEntries(lines);
            } finally {
                this.loadingMessages = false;
            }
            this.nextMessageIndex = 0;
        } catch (Exception exception) {
            if (notifyFailure && !this.isWorldUnavailable()) {
                this.printFileError("modules.Spammer.FileOpenFailed", exception);
            }
        }
    }

    private void onMessagesChanged() {
        if (!this.loadingMessages) {
            this.saveMessages();
        }
    }

    private void saveMessages() {
        try {
            Path filePath = this.resolveWritableFile();
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, this.messagesSetting.getValue(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            if (!this.isWorldUnavailable()) {
                this.printFileError("modules.Spammer.FileSaveFailed", exception);
            }
        }
    }

    private void printFileError(String translationKey, Exception exception) {
        String errorMessage = Translations.format(translationKey, this.pathSetting.getValue());
        MutableComponent literalComponent = Component.literal(errorMessage);
        UnaryOperator<Style> errorStyle = style -> style.withColor(ChatFormatting.RED);
        MutableComponent errorComponent = literalComponent.withStyle(errorStyle);
        String exceptionText = exception.toString();
        MessageSignature errorSignature = ChatUtils.createSignature(exceptionText.hashCode());
        ChatUtils.printChat(errorComponent, errorSignature, ChatUtils.MessagePriority.LOW);
    }

    @Override
    public void onEnable() {
        this.loadMessages(false);
    }

    @EventHandler
    private void onConnectionSendPacketEvent(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundChatPacket) {
            if (this.smartDelaySetting.getValue()) {
                this.sendTimer.reset();
            }
        }
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        Float delayValue = this.delaySetting.getValue();
        double delaySeconds = delayValue.floatValue();
        if (!this.sendTimer.hasElapsed(delaySeconds, TimeUnit.SECONDS)) {
            return;
        }
        List<String> messagesSnapshot = this.messagesSetting.getValue();
        int messageCount = messagesSnapshot.size();
        if (messageCount == 0) {
            return;
        }
        int selectedIndex;
        if (this.randomSetting.getValue() && messageCount > 1) {
            selectedIndex = this.messageRandom.nextInt(messageCount);
        } else {
            selectedIndex = this.nextMessageIndex % messageCount;
        }
        ChatUtils.sendChatOrCommand(messagesSnapshot.get(selectedIndex));
        this.nextMessageIndex = (this.nextMessageIndex + 1) % messageCount;
        this.sendTimer.reset();
        if (this.autoDisableSetting.getValue()) {
            this.toggle();
        }
    }
}
