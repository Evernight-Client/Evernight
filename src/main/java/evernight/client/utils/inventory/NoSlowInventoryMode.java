package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.events.player.InventoryActionEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.modules.movement.NoSlow.NoSlowModule;

public class NoSlowInventoryMode implements ClientAccess {
    protected final NoSlowModule noSlow;

    public NoSlowInventoryMode(NoSlowModule noSlow) {
        this.noSlow = noSlow;
    }

    public void onPacketSend(PacketEvent.Send event) {
    }

    public void onTickStart(LocalPlayerPreTickEvent event) {
    }

    public void onInventoryAction(InventoryActionEvent event) {
    }

    public void beforeMenuClick(MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick event) {
    }

    public void onInventoryClickIntercept(
            MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash event) {
    }

    public void onPlayerAiStep() {
    }

    public void onTickBeforePassengerCheck() {
    }
}