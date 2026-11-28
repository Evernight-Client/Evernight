package evernight.client.macro;

import evernight.client.modules.Keybind;

public class QueuedMacro extends Macro {
    private int nextCommandIndex = 0;

    public QueuedMacro(String name, Keybind keybind) {
        super(name, MacroType.QUEUE, keybind);
    }

    @Override
    public void run() {
        if (this.commands.isEmpty()) {
            return;
        }
        this.nextCommandIndex = this.nextCommandIndex % this.commands.size();
        String commandGroup = (String) this.commands.get(this.nextCommandIndex);
        String[] commands = commandGroup.split(";");
        for (String command : commands) {
            Macro.executeCommands(command);
        }
        this.nextCommandIndex++;
    }
}