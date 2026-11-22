package evernight.client.modules.misc.AutoRespawn;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.task.TaskScheduler;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.chat.ChatUtils;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;

public class AutoRespawnModule extends Module {
    private final ElapsedTimer respawnTimer = new ElapsedTimer();
    private final Setting<Boolean> saySetting;
    private final Setting<String> messageSetting;

    public AutoRespawnModule() {
        super("AutoRespawn", ModuleCategory.MISC);
        Boolean sayDefault = false;
        BooleanSetting say = new BooleanSetting("Say", sayDefault);
        say.markVisibilityDependency();
        this.saySetting = this.add(say);
        StringSetting message = new StringSetting("Message", "/kit cattyn");
        Setting<?>[] dependencies = new Setting<?>[1];
        dependencies[0] = this.saySetting;
        this.messageSetting = this.add(message.dependsOn(dependencies));
        this.setDrawn(false);
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        if (ClientAccess.evernightMinecraft.screen instanceof DeathScreen) {
            ElapsedTimer timer = this.respawnTimer;
            if (timer.hasElapsed(100L)) {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                player.respawn();
                this.respawnTimer.reset();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundRespawnPacket) {
            Setting<Boolean> saySnapshot = this.saySetting;
            Boolean say = saySnapshot.getValue();
            if (say) {
                TaskScheduler scheduler = ClientContext.taskScheduler;
                Runnable sendMessage = () -> {
                    Setting<String> messageSnapshot = this.messageSetting;
                    String message = messageSnapshot.getValue();
                    ChatUtils.sendChatOrCommand(message);
                };
                scheduler.scheduleRotationTask(sendMessage, 2);
            }
        }
        if (event.getPacket() instanceof ClientboundPlayerCombatKillPacket) {
            this.respawnTimer.reset();
        }
    }
}