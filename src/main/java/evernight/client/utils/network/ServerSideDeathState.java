package evernight.client.utils.network;

public interface ServerSideDeathState {
    boolean isServerSideDead();

    void setServerSideDead(boolean serverSideDead);
}