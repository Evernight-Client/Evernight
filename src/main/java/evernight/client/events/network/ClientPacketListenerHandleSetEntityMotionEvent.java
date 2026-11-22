package evernight.client.events.network;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import evernight.client.events.core.CancellableEvent;

public final class ClientPacketListenerHandleSetEntityMotionEvent extends CancellableEvent {
    private final int entityId;
    private float velocityX;
    private float velocityY;
    private float velocityZ;
    private boolean velocityModified;

    public ClientPacketListenerHandleSetEntityMotionEvent(int entityId, float velocityX, float velocityY, float velocityZ) {
        this.entityId = entityId;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
    }

    public ClientPacketListenerHandleSetEntityMotionEvent(ClientboundSetEntityMotionPacket packet) {
        this.entityId = packet.getId();
        this.velocityX = (float) packet.getMovement().x();
        this.velocityY = (float) packet.getMovement().y();
        this.velocityZ = (float) packet.getMovement().z();
    }

    public float getVelocityY() {
        return this.velocityY;
    }

    public void setVelocityY(float velocityY) {
        this.velocityY = velocityY;
        this.velocityModified = true;
    }

    public float getVelocityX() {
        return this.velocityX;
    }

    public void setVelocityX(float velocityX) {
        this.velocityX = velocityX;
        this.velocityModified = true;
    }

    public Vec3 getVelocity() {
        return new Vec3(this.velocityX, this.velocityY, this.velocityZ);
    }

    public void setVelocity(Vec3 velocity) {
        this.setVelocity((float) velocity.x(), (float) velocity.y(), (float) velocity.z());
    }

    public float getVelocityZ() {
        return this.velocityZ;
    }

    public void setVelocityZ(float velocityZ) {
        this.velocityZ = velocityZ;
        this.velocityModified = true;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public boolean isVelocityModified() {
        return this.velocityModified;
    }

    public void setVelocity(float velocityX, float velocityY, float velocityZ) {
        this.setVelocityX(velocityX);
        this.setVelocityY(velocityY);
        this.setVelocityZ(velocityZ);
    }
}