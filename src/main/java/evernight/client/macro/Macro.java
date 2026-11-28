package evernight.client.macro;

import evernight.client.NamedFeature;
import evernight.client.managers.registry.CommandRegistry;
import evernight.client.modules.Keybind;

import java.util.ArrayList;
import java.util.List;

public abstract class Macro extends NamedFeature {
    protected final List commands = new ArrayList();
    private final MacroType type;
    private final Keybind keybind;
    private boolean toggled;

    public Macro(String name, MacroType type, Keybind keybind) {
        super(name);
        this.type = type;
        this.keybind = keybind;
        this.toggled = true;
    }

    public static void executeCommands(String commandGroup) {
        for (String command : commandGroup.split(";")) {
            CommandRegistry.executeCommand(command.trim());
        }
    }

    public abstract void run();

    public boolean isToggled() {
        return this.toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public Keybind getKeybind() {
        return this.keybind;
    }

    public List getCommands() {
        return this.commands;
    }

    public MacroType getType() {
        return this.type;
    }
}