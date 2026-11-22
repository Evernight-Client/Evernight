package evernight.client.events.player;

import evernight.client.ClientAccess;
import evernight.client.events.core.CancellableEvent;

public final class RotationRequestEvent extends CancellableEvent implements ClientAccess {
    public float getPlayerPitch() {
        return ClientAccess.evernightMinecraft.player.getXRot();
    }

    public float getPlayerYaw() {
        return ClientAccess.evernightMinecraft.player.getYRot();
    }
}