package evernight.client.modules.combat.AntiBot;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.managers.player.FakeplayerManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.UUID;

public class AntiBotModule extends Module {
    private final Setting<Boolean> illegalCharsSetting;
    private final Setting<Boolean> illegalNamesSetting;

    public AntiBotModule() {
        super("AntiBot", ModuleCategory.COMBAT);
        this.illegalCharsSetting = this.add(new BooleanSetting("IllegalChars", false));
        this.illegalNamesSetting = this.add(new BooleanSetting("IllegalNames", false));
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerTickEvent event) {
        Player lastBot = null;
        Iterator<?> players = ClientAccess.evernightMinecraft.level.players().iterator();
        while (players.hasNext()) {
            Player candidate = (Player) players.next();
            if (this.isBot(candidate)) {
                lastBot = candidate;
            }
        }
        if (lastBot != null) {
            RemovalReason removalReason = RemovalReason.KILLED;
            lastBot.setRemoved(removalReason);
        }
    }

    private boolean isBot(Player candidate) {
        if (ClientAccess.evernightMinecraft.player == candidate
                || candidate instanceof FakeplayerManager.SimulatedPlayer) {
            return false;
        }
        String name = candidate.getGameProfile().name();
        if (name == null) {
            return true;
        }
        int nameLength = name.length();
        if (this.illegalNamesSetting.getValue()) {
            if (nameLength < 3) {
                return true;
            }
            if (nameLength > 16) {
                return true;
            }
        }
        if (this.illegalCharsSetting.getValue()) {
            for (int characterIndex = 0; characterIndex < nameLength; characterIndex++) {
                char character = name.charAt(characterIndex);
                char lowercaseCharacter = Character.toLowerCase(character);
                if (lowercaseCharacter >= '0') {
                    if (lowercaseCharacter <= '9') {
                        continue;
                    }
                }
                if (lowercaseCharacter != '_') {
                    if (lowercaseCharacter < 'a') {
                        return true;
                    }
                    if (lowercaseCharacter > 'z') {
                        return true;
                    }
                }
            }
        }
        ClientPacketListener connectionSnapshot = ClientAccess.evernightMinecraft.player.connection;
        UUID profileId = candidate.getGameProfile().id();
        PlayerInfo playerInfo = connectionSnapshot.getPlayerInfo(profileId);
        return playerInfo == null || playerInfo.getProfile() == null && candidate.hasCustomName();
    }
}