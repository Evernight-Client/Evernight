package evernight.client.gui.core;

public interface CloseState {
    CloseState ALWAYS_OPEN = () -> false;

    boolean isClosed();

    default boolean isOpen() {
        return !this.isClosed();
    }
}