package evernight.client.access;

public interface LocalPlayerTickAccess {
    void sendMovementPacketsWrapper();

    void superTick();

    void resetEvent();

    void evernight$updateServer(float yaw, float pitch);

    void resetRotations();
}