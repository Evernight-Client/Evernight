package evernight.client.modules.misc.ExtraScreenshot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.audio.BuiltinSound;
import evernight.client.audio.SoundClip;
import evernight.client.managers.audio.SoundManager;
import evernight.client.managers.config.ScreenshotCredentialStore;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.font.FontRenderer;
import evernight.client.render.text.Translations;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.collections.NamedIdentifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.IdentifierSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.network.HttpRequests;

public class ExtraScreenshotModule extends Module {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final long uploadCooldownMillis = 1500L;
    private static final HttpClient uploadClient = HttpClient.newHttpClient();
    public final Setting<ModeChoice> modeSetting;
    public final Setting<Boolean> deleteFileSetting;
    public final Setting<String> clientIdSetting;
    private final Setting<Boolean> soundSetting;
    private final Setting<NamedIdentifier> pathSetting;
    private final Setting<Float> volumeSetting;
    private final ElapsedTimer uploadTimer;
    private int cooldownMessageIndex;
    private boolean syncingCredential;

    public ExtraScreenshotModule() {
        super("ExtraScreenshot", ModuleCategory.MISC);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.COPY));
        Predicate<Object> deleteFile = ignored -> this.modeSetting.getValue() != ModeChoice.NONE;
        this.deleteFileSetting = this.add(new BooleanSetting("DeleteFile", false, deleteFile));
        Predicate<Object> clientIdVisible = ignored -> this.modeSetting.getValue() == ModeChoice.IMGUR;
        this.clientIdSetting = this.add(new StringSetting("ClientId",
                ScreenshotCredentialStore.DEFAULT_IMGUR_CLIENT_ID, clientIdVisible));
        // The credential is persisted in screenshot.json, never in modules.json or presets.
        this.clientIdSetting.setExcludedFromConfig(true);
        this.clientIdSetting.setChangeCallback(this::pushClientIdToStore);
        BooleanSetting sound = new BooleanSetting("Sound", false);
        sound.markVisibilityDependency();
        this.soundSetting = this.add(sound);
        this.pathSetting = this.add(new IdentifierSetting("Path", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.soundSetting));
        this.volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.soundSetting));
        this.uploadTimer = new ElapsedTimer();
        this.cooldownMessageIndex = 0;
    }

    /** Keeps screenshot.json in sync while the ClickGUI edits the credential row. */
    private void pushClientIdToStore() {
        if (this.syncingCredential) {
            return;
        }
        ScreenshotCredentialStore store = ClientContext.screenshotCredentialStore;
        if (store != null) {
            store.setClientId(this.clientIdSetting.getValue());
        }
    }

    /** Applies the standalone credential to the ClickGUI row after config load or preset import. */
    private void syncClientIdFromStore() {
        ScreenshotCredentialStore store = ClientContext.screenshotCredentialStore;
        if (store == null) {
            return;
        }
        String stored = store.getClientId();
        if (Objects.equals(this.clientIdSetting.getValue(), stored)) {
            return;
        }
        this.syncingCredential = true;
        try {
            this.clientIdSetting.setValue(stored == null ? "" : stored);
        } finally {
            this.syncingCredential = false;
        }
    }

    /**
     * Moves a {@code ClientId} written by older versions out of modules.json once, and only during
     * the normal config load. Preset imports must never pull a credential out of someone else's
     * preset, so {@link ClientContext#configurationLoading} gates this migration.
     */
    private void migrateLegacyClientId(JsonElement json) {
        ScreenshotCredentialStore store = ClientContext.screenshotCredentialStore;
        if (store == null || store.isLoadedFromFile() || !ClientContext.configurationLoading
                || !(json instanceof JsonObject moduleJson)) {
            return;
        }
        JsonElement settings = moduleJson.get("settings");
        if (!(settings instanceof JsonObject settingsJson)) {
            return;
        }
        JsonElement legacy = settingsJson.get("ClientId");
        if (legacy == null || legacy.isJsonNull()) {
            return;
        }
        String legacyClientId = legacy.getAsString();
        store.setClientId(legacyClientId);
        this.syncingCredential = true;
        try {
            this.clientIdSetting.setValue(legacyClientId);
        } finally {
            this.syncingCredential = false;
        }
    }

    @Override
    public void fromJson(JsonElement json) {
        super.fromJson(json);
        this.migrateLegacyClientId(json);
        this.syncClientIdFromStore();
    }

    private void playScreenshotSound() {
        Setting<Boolean> soundOption = this.soundSetting;
        Boolean playSound = soundOption.getValue();
        if (playSound) {
            SoundManager soundManagerSnapshot = ClientContext.soundManager;
            Setting<NamedIdentifier> pathOption = this.pathSetting;
            NamedIdentifier soundId = pathOption.getValue();
            SoundClip soundSnapshot = soundManagerSnapshot.getSound(soundId);
            Setting<Float> volumeOption = this.volumeSetting;
            float soundVolume = volumeOption.getValue();
            soundSnapshot.play(soundVolume);
        }
    }

    private ModeChoice getActiveMode() {
        ModeChoice mode = this.modeSetting.getValue();
        return mode == ModeChoice.COPY && GraphicsEnvironment.isHeadless() ? ModeChoice.NONE : mode;
    }

    public boolean isModeNone() {
        return this.getActiveMode() == ModeChoice.NONE;
    }

    /**
     * Builds the Imgur {@code Authorization} header from the player-configured credential.
     *
     * <p>Accepts a bare client ID, a complete {@code Client-ID ...} header value or a
     * {@code Bearer ...} access token, so pasting whatever Imgur gave you works either way.
     *
     * @return the header value, or {@code null} when nothing usable is configured
     */
    private String getImgurAuthorization() {
        String credential = this.clientIdSetting.getValue();
        if (credential == null) {
            return null;
        }
        credential = credential.trim();
        if (credential.isEmpty()) {
            return null;
        }
        if (credential.regionMatches(true, 0, "client-id ", 0, 10) || credential.regionMatches(true, 0, "bearer ", 0, 7)) {
            return credential;
        }
        return "Client-ID " + credential;
    }

    public void processScreenshotBytes(byte[] imageBytes) {
        ModeChoice mode = this.getActiveMode();
        if (mode == ModeChoice.IMGUR) {
            synchronized (this) {
                if (!this.uploadTimer.hasElapsed(uploadCooldownMillis)) {
                    this.cooldownMessageIndex = (this.cooldownMessageIndex + 1) % 3;
                    long remaining = uploadCooldownMillis - this.uploadTimer.elapsedMillis();
                    var message = Component.literal(Translations.format("modules.ExtraScreenshot.UploadCooldown", remaining)).withStyle(ChatFormatting.YELLOW);
                    ChatUtils.printChat(message, ChatUtils.createSignature(-34596741 + this.cooldownMessageIndex), ChatUtils.MessagePriority.LOW);
                    return;
                }
                this.uploadTimer.reset();
            }
        }
        try {
            this.playScreenshotSound();
        } catch (Exception failure) {
            LOGGER.warn("Could not play screenshot sound", failure);
        }
        if (mode != ModeChoice.IMGUR) this.uploadTimer.reset();
        if (mode == ModeChoice.NONE) return;
        try {
            if (mode == ModeChoice.COPY) {
                BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (source == null) throw new IOException("Unsupported screenshot format");
                BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.drawImage(source, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                this.copyImageToClipboard(new ClipboardImage(image));
                ChatUtils.printChat(Component.literal(Translations.ui("modules.ExtraScreenshot.CopiedToClipboard")), ChatUtils.moduleSignature(this));
                return;
            }
            String authorization = this.getImgurAuthorization();
            if (authorization == null) {
                ChatUtils.printChat(Component.literal(Translations.ui("modules.ExtraScreenshot.MissingClientId"))
                        .withStyle(ChatFormatting.RED), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID);
                return;
            }
            ChatUtils.printChat(Component.literal(Translations.ui("modules.ExtraScreenshot.Uploading")), ChatUtils.moduleSignature(this));
            String encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(imageBytes), StandardCharsets.UTF_8);
            HttpRequest request = evernight.client.utils.network.HttpRequests.post("https://api.imgur.com/3/image", BodyPublishers.ofString("image=" + encoded))
                    .header("Content-Type", "application/x-www-form-urlencoded").header("Authorization", authorization)
                    .timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response = evernight.client.utils.network.HttpRequests.send(request);
            if (response.statusCode() / 100 != 2) throw new IOException("Imgur returned HTTP " + response.statusCode());
            JsonObject json = ClientAccess.gson.fromJson(response.body(), JsonObject.class);
            if (json == null || !json.has("success") || !json.get("success").getAsBoolean() || !json.has("data")) {
                throw new IOException("Invalid upload response");
            }
            String link = json.getAsJsonObject("data").get("link").getAsString();
            URI uri = URI.create(link);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IOException("Invalid image link");
            }
            ClientAccess.evernightMinecraft.execute(() -> {
                ClientAccess.evernightMinecraft.keyboardHandler.setClipboard(link);
                var notification = Component.literal(Translations.ui("modules.ExtraScreenshot.CopiedLink")).withStyle(style -> style.withClickEvent(new OpenUrl(uri)));
                ChatUtils.printChat(notification, -43296436);
            });
        } catch (Exception failure) {
            LOGGER.warn("Screenshot {} failed", mode == ModeChoice.COPY ? "copy" : "upload", failure);
            var message = Component.literal(mode == ModeChoice.COPY
                            ? Translations.ui("modules.ExtraScreenshot.CopyFailed")
                            : Translations.ui("modules.ExtraScreenshot.UploadFailed"))
                    .withStyle(ChatFormatting.RED);
            ChatUtils.printChat(message, ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID);
        }
    }

    @Override
    public String getInfo() {
        Setting<ModeChoice> modeOption = this.modeSetting;
        return FontRenderer.capitalize(modeOption.getValue());
    }

    @Override
    public void onEnable() {
        this.syncClientIdFromStore();
        this.cooldownMessageIndex = 0;
    }

    public boolean shouldDeleteFile() {
        return !this.isModeNone() && this.deleteFileSetting.getValue();
    }

    private void copyImageToClipboard(ClipboardImage image) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Clipboard clipboard = toolkit.getSystemClipboard();
        clipboard.setContents(image, null);
    }

    public void processScreenshot(NativeImage image) {
        byte[] imageBytes;
        try {
            try {
                imageBytes = this.encodeScreenshot(image);
                if (imageBytes == null) {
                    throw new RuntimeException();
                }
            } catch (Exception encodingFailure) {
                Component message = Component.nullToEmpty(Translations.ui("modules.ExtraScreenshot.SaveFailed"));
                ChatUtils.printChat(message, ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.MID);
                return;
            }
        } finally {
            if (this.shouldDeleteFile()) {
                image.close();
            }
        }
        ClientAccess.executorService.submit(() -> {
            try {
                this.processScreenshotBytes(imageBytes);
            } catch (Exception processingFailure) {
                LOGGER.warn("Could not process screenshot", processingFailure);
            }
        });
    }

    private byte[] encodeScreenshot(NativeImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             WritableByteChannel channel = Channels.newChannel(output)) {
            if (!image.writeToChannel(channel)) throw new IOException("Screenshot encoding failed");
            return output.toByteArray();
        }
    }

    public enum ModeChoice implements NamedEntry {
        NONE("None"),
        COPY("Copy"),
        IMGUR("Imgur");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}