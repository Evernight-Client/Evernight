package evernight.client.macro;

import evernight.client.modules.Keybind;
import evernight.client.utils.core.ElapsedTimer;

import java.util.Iterator;

public class DoubleTapMacro extends Macro {
    private final ElapsedTimer tapTimer = new ElapsedTimer();

    public DoubleTapMacro(String name, Keybind keybind) {
        super(name, MacroType.DOUBLE_TAP, keybind);
    }

    @Override
    public void run() {
        if (this.tapTimer.hasElapsed(300L)) {
            this.tapTimer.reset();
        } else {
            Iterator commands = this.commands.iterator();
            while (commands.hasNext()) {
                String command = (String) commands.next();
                Macro.executeCommands(command);
            }
            this.tapTimer.clear();
        }
    }
}