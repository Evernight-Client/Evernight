package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LivingEntityRotationEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.world.FireworkRocketEntityTickEvent;

public abstract class ElytraFlyModeHandler implements ClientAccess {
    protected final ElytraFlyModule module;

    protected ElytraFlyModeHandler(ElytraFlyModule module) {
        this.module = module;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void handleFireworkTick(FireworkRocketEntityTickEvent event) {
    }

    public void handlePrePlayerTick(LocalPlayerPreTickEvent event) {
    }

    public void handleOutgoingPacket(PacketEvent.Send event) {
    }

    public void handlePlayerTick(LocalPlayerTickEvent event) {
    }

    public void handleMovement(LocalPlayerMoveEvent event) {
    }

    public void handleTravelRotation(LivingEntityRotationEvent event) {
    }

    public void handleClientTick(MinecraftRunTickEvent event) {
    }

    public void handleRotationManagerEvent(RotationRequestEvent event) {
    }

    public void handleIncomingPacket(PacketEvent.Receive event) {
    }

    public void handleSendPosition(LocalPlayerSendPositionEvent event) {
    }
}