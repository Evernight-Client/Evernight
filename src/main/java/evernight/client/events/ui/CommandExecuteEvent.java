package evernight.client.events.ui;
import evernight.client.events.core.CancellableEvent;

public final class CommandExecuteEvent extends CancellableEvent {
    private final String command;

    public CommandExecuteEvent(String command) {
        this.command = command;
    }

    public String getCommand() {
        return this.command;
    }
}