package evernight.client.settings.options;

import evernight.client.modules.Keybind;
import evernight.client.modules.misc.Macro;

import java.util.Iterator;

public class SimpleMacro extends Macro {
    public SimpleMacro(String name, Keybind keybind) {
        super(name, MacroType.SIMPLE, keybind);
    }

    @Override
    public void run() {
        Iterator commands = this.commands.iterator();
        while (commands.hasNext()) {
            String command = (String) commands.next();
            Macro.executeCommands(command);
        }
    }
}