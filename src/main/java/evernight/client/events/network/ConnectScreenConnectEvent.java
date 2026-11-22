package evernight.client.events.network;
import evernight.client.events.core.CancellableEvent;

public final class ConnectScreenConnectEvent extends CancellableEvent {
    private final String serverAddress;

    public ConnectScreenConnectEvent(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getServerAddress() {
        return this.serverAddress;
    }
}