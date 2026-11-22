package evernight.client.modules.misc.VisualRange;

import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.audio.BuiltinSound;
import evernight.client.audio.SoundClip;
import evernight.client.managers.registry.AliasRegistry;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.managers.audio.SoundManager;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.IdentifierSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.collections.NamedIdentifier;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.ClientChunkCacheReplaceWithPacketDataEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.world.ClientLevelAddEntityEvent;
import evernight.client.events.world.ClientLevelRemoveEntityEvent;

public class VisualRangeModule extends Module {
    private static final int enderPearlMessageId;

    static {
        int enderPearlHash = ("EnderPearl").hashCode();
        enderPearlMessageId = -Math.abs(enderPearlHash);
    }

    private final Setting<Boolean> joinSetting;
    private final Setting<Boolean> leaveSetting;
    private final Setting<Boolean> targetsSetting;
    private final Setting<Boolean> playersSetting;
    private final Setting<Boolean> friendsSetting;
    private final Setting<Boolean> enemiesSetting;
    private final Setting<Boolean> nakedsSetting;
    private final Setting<Boolean> signsSetting;
    private final Setting<Boolean> enderPearlsSetting;
    private final Setting<Boolean> playSoundSetting;
    private final Setting<Boolean> onlyEnemiesSetting;
    private final Setting<NamedIdentifier> soundSetting;
    private final Setting<Float> volumeSetting;

    public VisualRangeModule() {
        super("VisualRange", ModuleCategory.MISC);
        this.joinSetting = this.add(new BooleanSetting("Join", true));
        this.leaveSetting = this.add(new BooleanSetting("Leave", true));
        this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup());
        this.playersSetting = this.add(new BooleanSetting("Players", true).dependsOn(this.targetsSetting));
        this.friendsSetting = this.add(new BooleanSetting("Friends", false).dependsOn(this.targetsSetting));
        this.enemiesSetting = this.add(new BooleanSetting("Enemies", true).dependsOn(this.targetsSetting));
        this.nakedsSetting = this.add(new BooleanSetting("Nakeds", false).dependsOn(this.targetsSetting));
        this.signsSetting = this.add(new BooleanSetting("Signs", false).dependsOn(this.targetsSetting));
        this.enderPearlsSetting = this.add(new BooleanSetting("EnderPearls", false).dependsOn(this.targetsSetting));
        this.playSoundSetting = this.add(new BooleanSetting("PlaySound", false).markVisibilityDependency());
        this.onlyEnemiesSetting = this.add(new BooleanSetting("OnlyEnemies", false).dependsOn(this.playSoundSetting));
        this.soundSetting = this.add(new IdentifierSetting("Sound", BuiltinSound.WARNING.getIdentifier()).dependsOn(this.playSoundSetting));
        this.volumeSetting = this.add(new NumberSetting<>("Volume", 1.0F, 0.0F, 1.0F).dependsOn(this.playSoundSetting));
        this.setDrawn(false);
    }

    private void playAlertSound() {
        Setting<Boolean> playSoundOption = this.playSoundSetting;
        if (playSoundOption.getValue()) {
            SoundManager soundManagerSnapshot = ClientContext.soundManager;
            Setting<NamedIdentifier> soundOption = this.soundSetting;
            NamedIdentifier soundId = soundOption.getValue();
            SoundClip soundSnapshot = SoundManager.getSound(soundManagerSnapshot, soundId);
            Setting<Float> volumeOption = this.volumeSetting;
            float soundVolume = volumeOption.getValue();
            soundSnapshot.play(soundVolume);
        }
    }

    @EventHandler
    private void onClientLevelAddEntityEvent(ClientLevelAddEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            TaskScheduler scheduler = ClientContext.taskScheduler;
            Runnable joinNotificationTask = () -> {
                Player targetPlayer = player;
                if (!this.shouldNotifyPlayer(targetPlayer)) {
                    return;
                }
                Setting<Boolean> joinOption = this.joinSetting;
                if (!(joinOption.getValue())) {
                    return;
                }
                GameProfile profile = player.getGameProfile();
                String playerName = profile.name();
                Color defaultColor = Color.WHITE;
                SocialRegistry colorManager = ClientContext.socialRegistry;
                Color playerColor = colorManager.getColor(playerName, defaultColor);
                Object[] formatArguments = new Object[(2)];
                AliasRegistry nameFormatterSnapshot = ClientContext.aliasRegistry;
                formatArguments[0] = nameFormatterSnapshot.getPlayerAlias(playerName);
                formatArguments[1] = ChatFormatting.WHITE;
                String message = Translations.format("modules.VisualRange.Entered", formatArguments);
                MutableComponent notification = Component.literal(message);
                UnaryOperator<Style> colorStyle = style -> {
                    int colorHash = playerColor.hashCode();
                    return style.withColor(colorHash);
                };
                notification = notification.withStyle(colorStyle);
                Vec3 position = player.position();
                this.addPositionInteractions(notification, position);
                int nameHash = playerName.hashCode();
                int messageId = Math.abs(nameHash) * -1;
                messageId /= (2);
                MessageSignature signature = ChatUtils.createSignature(messageId);
                ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.MID;
                ChatUtils.printChat(notification, signature, priority);
                SocialRegistry enemyManagerSnapshot = ClientContext.socialRegistry;
                if (!enemyManagerSnapshot.isEnemyName(playerName)) {
                    Setting<Boolean> onlyEnemiesOption = this.onlyEnemiesSetting;
                    if (onlyEnemiesOption.getValue()) {
                        return;
                    }
                }
                this.playAlertSound();
            };
            scheduler.scheduleRotationTask(joinNotificationTask, 1);
        }
    }

    private void addPositionInteractions(MutableComponent message, Vec3 position) {
        Object[] formatArguments = new Object[(3)];
        double x = position.x();
        formatArguments[0] = x;
        double y = position.y();
        formatArguments[1] = y;
        double z = position.z();
        formatArguments[(2)] = z;
        String coordinates = ("%.0f, %.0f, %.0f").formatted(formatArguments);
        String hoverMessage = Translations.format("modules.VisualRange.HoverAt", coordinates);
        MutableComponent hoverText = Component.literal(hoverMessage);
        UnaryOperator<Style> hoverStyle = style -> {
            HoverEvent hoverEvent = new ShowText(hoverText);
            return style.withHoverEvent(hoverEvent);
        };
        message.withStyle(hoverStyle);
        UnaryOperator<Style> clickStyle = style -> {
            String highlightCoordinates = coordinates.replace(",", "");
            String highlightCommand = "highlight " + highlightCoordinates;
            ClickEvent clickEvent = ChatUtils.suggestCommand(highlightCommand);
            return style.withClickEvent(clickEvent);
        };
        message.withStyle(clickStyle);
    }

    @EventHandler
    private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
        ClientLevel levelSnapshot = ClientAccess.evernightMinecraft.level;
        int entityId = event.getId();
        if (!(levelSnapshot.getEntity(entityId) instanceof Player player)) {
            return;
        }
        if (!event.isEntityPresent() || !this.shouldNotifyPlayer(player)) {
            return;
        }
        Setting<Boolean> leaveOption = this.leaveSetting;
        if (!(leaveOption.getValue())) {
            return;
        }
        GameProfile profile = player.getGameProfile();
        String playerName = profile.name();
        Color defaultColor = Color.WHITE;
        SocialRegistry colorManager = ClientContext.socialRegistry;
        Color playerColor = colorManager.getColor(playerName, defaultColor);
        Object[] formatArguments = new Object[(2)];
        AliasRegistry nameFormatterSnapshot = ClientContext.aliasRegistry;
        formatArguments[0] = nameFormatterSnapshot.getPlayerAlias(playerName);
        formatArguments[1] = ChatFormatting.WHITE;
        String message = Translations.format("modules.VisualRange.Left", formatArguments);
        MutableComponent notification = Component.literal(message);
        UnaryOperator<Style> colorStyle = style -> {
            int colorHash = playerColor.hashCode();
            return style.withColor(colorHash);
        };
        notification = notification.withStyle(colorStyle);
        Vec3 position = player.position();
        this.addPositionInteractions(notification, position);
        int nameHash = playerName.hashCode();
        int messageId = Math.abs(nameHash) * -1;
        messageId /= (2);
        MessageSignature signature = ChatUtils.createSignature(messageId);
        ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.LOW;
        ChatUtils.printChat(notification, signature, priority);
    }

    @EventHandler
    private void onClientChunkCacheReplaceWithPacketDataEvent(ClientChunkCacheReplaceWithPacketDataEvent event
    ) {
        Setting<Boolean> signsOption = this.signsSetting;
        if (signsOption.getValue()) {
            AtomicBoolean foundSigns = new AtomicBoolean(false);
            LevelChunk chunk = event.getChunk();
            Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
            BiConsumer<BlockPos, BlockEntity> notifySign = (blockPosition, blockEntity) -> {
                if (blockEntity instanceof SignBlockEntity sign) {
                    int positionHash = blockPosition.hashCode();
                    int signMessageId = -Math.abs(positionHash);
                    MutableComponent notification = Component.literal(Translations.ui("modules.VisualRange.FoundSign"));
                    boolean empty = true;
                    SignText frontTextSnapshot = sign.getFrontText();
                    boolean filtered = false;
                    for (Component line : frontTextSnapshot.getMessages(filtered)) {
                        String lineText = line.getString();
                        String strippedLine = ChatFormatting.stripFormatting(lineText);
                        if (!strippedLine.isBlank()) {
                            notification.append(line);
                            notification.append((" "));
                            empty = false;
                        }
                    }
                    if (empty) {
                        notification.append(Translations.ui("modules.VisualRange.EmptySign"));
                    }
                    UnaryOperator<Style> colorStyle = style -> {
                        ChatFormatting textColor = ChatFormatting.WHITE;
                        return style.applyFormat(textColor);
                    };
                    notification.withStyle(colorStyle);
                    BlockPos signPosition = sign.getBlockPos();
                    Vec3 position = signPosition.getCenter();
                    this.addPositionInteractions(notification, position);
                    MessageSignature signature = ChatUtils.createSignature(signMessageId);
                    ChatUtils.printChat(notification, signature);
                    foundSigns.set(true);
                }
            };
            blockEntities.forEach(notifySign);
            if (foundSigns.get()) {
                this.playAlertSound();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundAddEntityPacket packet)
                || packet.getType() != EntityType.ENDER_PEARL) {
            return;
        }
        Setting<Boolean> enderPearlsOption = this.enderPearlsSetting;
        if (!enderPearlsOption.getValue()) {
            return;
        }
        MutableComponent notification = Component.literal(Translations.ui("modules.VisualRange.EnderPearl"));
        Vec3 position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        this.addPositionInteractions(notification, position);
        MessageSignature signature = ChatUtils.createSignature((enderPearlMessageId));
        ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.MID;
        ChatUtils.printChat(notification, signature, priority);
        this.playAlertSound();
    }

    private boolean shouldNotifyPlayer(Player player) {
        if (ClientAccess.evernightMinecraft.player == player) {
            return false;
        }
        GameProfile profile = player.getGameProfile();
        String playerName = profile.name();
        LocalPlayer localPlayer = ClientAccess.evernightMinecraft.player;
        GameProfile localProfile = localPlayer.getGameProfile();
        if (playerName.equals(localProfile.name())) {
            return false;
        }
        SocialRegistry friendManager = ClientContext.socialRegistry;
        if (friendManager.isFriendPlayer(player)) {
            return this.friendsSetting.getValue();
        }
        SocialRegistry enemyManager = ClientContext.socialRegistry;
        if (enemyManager.isEnemyPlayer(player)) {
            return this.enemiesSetting.getValue();
        }
        if (!PlayerStateUtils.hasArmor(player)) {
            return this.nakedsSetting.getValue();
        }
        return this.playersSetting.getValue();
    }
}