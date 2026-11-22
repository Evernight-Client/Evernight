package evernight.client.events.network;
import evernight.client.events.core.CancellableEvent;

public final class ClientPacketListenerSendChatEvent extends CancellableEvent {
    private String message;

    public ClientPacketListenerSendChatEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}