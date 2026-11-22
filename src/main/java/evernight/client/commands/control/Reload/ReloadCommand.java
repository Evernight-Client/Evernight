package evernight.client.commands.control.Reload;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.config.ConfigurationManager;
import evernight.client.managers.registry.ShaderRegistry;
import evernight.client.managers.audio.SoundManager;
import evernight.client.render.text.Translations;
import evernight.client.utils.chat.ChatUtils;
import evernight.mixin.minecraft.render.TextureManagerAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import evernight.client.commands.Command;

public final class ReloadCommand extends Command {
    private static final byte[] PNG_SIGNATURE;

    static {
        byte[] signature = new byte[8];
        signature[0] = (byte) (-119);
        signature[1] = (byte) (80);
        signature[(2)] = (byte) (78);
        signature[(3)] = (byte) (71);
        signature[(4)] = (byte) (13);
        signature[(5)] = (byte) (10);
        signature[(6)] = (byte) (26);
        signature[(7)] = (byte) (10);
        PNG_SIGNATURE = signature;
    }

    private CompletableFuture reloadFuture;

    public ReloadCommand() {
        super("reload");
        this.reloadFuture = CompletableFuture.completedFuture(null);
        this.setAliases("rl");
    }

    private static void validatePngSignature(Path imagePath) throws IOException {
        try (InputStream input = Files.newInputStream(imagePath)) {
            int signatureLength = PNG_SIGNATURE.length;
            byte[] actualSignature = input.readNBytes(signatureLength);
            if (!Arrays.equals(actualSignature, PNG_SIGNATURE)) {
                throw new RuntimeException(Translations.ui("commands.Reload.NotPng"));
            }
        }
    }

    @Override
    public void exec(LiteralArgumentBuilder commandBuilder) {
        commandBuilder.executes((com.mojang.brigadier.Command<Object>) commandContext -> {
                    CompletableFuture previousReload = this.reloadFuture;
                    if (!previousReload.isDone()) {
                        return 0;
                    }
                    MutableComponent reloadingMessage = Component.literal(Translations.ui("commands.Reload.Reloading"));
                    ReloadCommand command = this;
                    ChatUtils.printChat(reloadingMessage, ChatUtils.objectSignature(command));
                    this.reloadFuture = CompletableFuture.runAsync(this::reloadResources,
                            ClientAccess.executorService);
                    this.reloadFuture.whenComplete(
                            (BiConsumer<Void, Throwable>) (ignoredResult, ignoredFailure) -> {
                                MutableComponent completionMessage = Component.literal(Translations.ui("commands.Reload.Complete")).withStyle(style -> {
                                    ChatFormatting format = ChatFormatting.GREEN;
                                    return style.applyFormat(format);
                                });
                                ReloadCommand completedCommand = this;
                                ChatUtils.printChat(completionMessage, ChatUtils.objectSignature(completedCommand));
                            }
                    );
                    return 1;
                }
        );
    }

    private void reloadResources() {
        ClientAccess.evernightMinecraft.execute(() -> {
            ShaderRegistry queuedShaderRegistry = ClientContext.shaderRegistry;
            queuedShaderRegistry.reloadAll();
        });
        ConfigurationManager directoryManager = ClientContext.configurationManager;
        directoryManager.prepareDirectories();
        SoundManager soundManager = ClientContext.soundManager;
        soundManager.reload();
        CompletableFuture<HashSet> textureSnapshotFuture = new CompletableFuture<>();
        ClientAccess.evernightMinecraft.execute(() -> {
            HashSet mountedSnapshot = new HashSet();
            Iterator textureIterator = ((TextureManagerAccessor) ClientAccess.evernightMinecraft.getTextureManager())
                    .getAllTextures().keySet().iterator();
            while (textureIterator.hasNext()) {
                Identifier texture = (Identifier) textureIterator.next();
                if (texture.getNamespace().equals("evernight-mount")) {
                    mountedSnapshot.add(texture);
                }
            }
            textureSnapshotFuture.complete(mountedSnapshot);
        });
        HashSet mountedTextures = textureSnapshotFuture.join();
        Iterator textures = mountedTextures.iterator();
        while (textures.hasNext()) {
            Identifier mountedTexture = (Identifier) textures.next();
            Path dataDirectory = ConfigurationManager.ROOT_DIRECTORY;
            Path imagePath = dataDirectory.resolve(mountedTexture.getPath());
            try {
                validatePngSignature(imagePath);
            } catch (IOException | RuntimeException invalidImage) {
                String errorDetail = invalidImage.getMessage();
                String errorText = Translations.ui("commands.Reload.InvalidImagePrefix") + errorDetail;
                MutableComponent errorMessage = Component.literal(errorText).withStyle(style -> {
                    ChatFormatting format = ChatFormatting.RED;
                    return style.applyFormat(format);
                });
                ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-999));
                continue;
            }
            ClientAccess.evernightMinecraft.executeIfPossible(() -> {
                Minecraft releaseClient = ClientAccess.evernightMinecraft;
                TextureManager textureManager = releaseClient.getTextureManager();
                textureManager.release((mountedTexture));
                Minecraft registrationClient = ClientAccess.evernightMinecraft;
                textureManager = registrationClient.getTextureManager();
                SimpleTexture replacementTexture = new SimpleTexture(mountedTexture);
                textureManager.registerAndLoad((mountedTexture), replacementTexture);
            });
        }
    }
}