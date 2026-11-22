package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.modules.movement.Velocity.VelocityModule;
import evernight.client.events.network.ClientPacketListenerHandleExplosionEvent;
import evernight.client.events.network.ClientPacketListenerHandleSetEntityMotionEvent;
import evernight.client.events.network.MultiPlayerGameModeUseItemOnEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.world.FishingHookPullEntityEvent;

public abstract class VelocityModeHandler implements ClientAccess {
    protected final VelocityModule velocityModule;

    public VelocityModeHandler(VelocityModule velocityModule) {
        this.velocityModule = velocityModule;
    }

    public void onDisable() {
    }

    public void onExplosion(ClientPacketListenerHandleExplosionEvent event) {
    }

    public void onEntityMotion(ClientPacketListenerHandleSetEntityMotionEvent event) {
    }

    public void onSendPacket(PacketEvent.Send event) {
    }

    public void onUseItemOn(MultiPlayerGameModeUseItemOnEvent event) {
    }

    public void onFishingPull(FishingHookPullEntityEvent event) {
    }

    public void onSendPosition(LocalPlayerSendPositionEvent event) {
    }

    public void onReceivePacket(PacketEvent.Receive event) {
    }

    public void onTick() {
    }
}