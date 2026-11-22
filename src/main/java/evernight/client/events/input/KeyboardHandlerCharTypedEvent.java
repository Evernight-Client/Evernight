package evernight.client.events.input;
import evernight.client.events.core.CancellableEvent;

public final class KeyboardHandlerCharTypedEvent extends CancellableEvent {
    private final int codePoint;

    public KeyboardHandlerCharTypedEvent(int codePoint) {
        this.codePoint = codePoint;
    }

    public String getText() {
        return Character.toString(this.codePoint);
    }

    public char getCharacter() {
        return this.getText().charAt(0);
    }
}