package evernight.client.modules.misc.Fakeplayer;

import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.gui.screens.misc.FakeplayerScreen;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.StringSetting;

import net.minecraft.network.protocol.game.ClientboundLoginPacket;

/**
 * ClickGUI entry for the fake player.
 *
 * <p>The module switch is the feature switch: enabling spawns the dummy with
 * {@link #nameSetting} and disabling removes it again. The graphical control panel opened through the
 * one-shot {@code Panel} action mirrors that state, and keeps exposing the kill, record, playback and
 * logout spot actions. Enabling while no world is loaded defers the spawn until the next world join,
 * so a config that restores the module as enabled does not try to spawn in the menu.
 */
public final class FakeplayerModule extends Module {
    private final Setting<String> nameSetting;
    private final Setting<Boolean> panelSetting;
    private boolean spawnPending;

    public FakeplayerModule() {
        super("Fakeplayer", ModuleCategory.MISC);
        this.nameSetting = this.add(new StringSetting("Name", "Herobrine"));
        this.panelSetting = this.add(new BooleanSetting("Panel", false));
        // The action row always falls back to false, so keep the transient value out of the config.
        this.panelSetting.setExcludedFromConfig(true);
        this.panelSetting.setChangeCallback(() -> {
            if (this.panelSetting.getValue()) {
                new FakeplayerScreen(this).open();
                this.panelSetting.setValue(false);
            }
        });
    }

    public Setting<String> getNameSetting() {
        return this.nameSetting;
    }

    @Override
    public void onEnable() {
        this.spawnPending = !ClientContext.fakeplayerManager.spawn(this.nameSetting.getValue());
    }

    @Override
    public void onDisable() {
        this.spawnPending = false;
        ClientContext.fakeplayerManager.remove();
    }

    @EventHandler
    private void requestSpawnOnWorldJoin(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundLoginPacket) {
            this.spawnPending = true;
        }
    }

    @EventHandler
    private void retryPendingSpawn(LocalPlayerSendPositionEvent event) {
        if (!this.spawnPending || event.getStage() != EventStage.Pre) {
            return;
        }
        if (ClientContext.fakeplayerManager.spawn(this.nameSetting.getValue())) {
            this.spawnPending = false;
        }
    }
}
