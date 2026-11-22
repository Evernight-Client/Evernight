package evernight.client.events.ui;

import evernight.client.modules.misc.Macro;
import evernight.client.events.core.CancellableEvent;

public final class MacroTriggerEvent extends CancellableEvent {
    private final Macro macro;

    public MacroTriggerEvent(Macro macro) {
        this.macro = macro;
    }

    public Macro getMacro() {
        return this.macro;
    }
}