package evernight.client.events.input;

import evernight.client.utils.core.InputUtils;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import evernight.client.events.core.CancellableEvent;

public class KeyboardInputTickEvent extends CancellableEvent {
    private final ClientInput input;

    public KeyboardInputTickEvent(ClientInput input) {
        this.input = input;
    }

    public ClientInput getInput() {
        return input;
    }

    public void reset() {
        InputUtils.clearKeyPresses(input);
        input.moveVector = Vec2.ZERO;
    }

    public static class Pre extends KeyboardInputTickEvent {
        public Pre(ClientInput input) {
            super(input);
        }
    }
}