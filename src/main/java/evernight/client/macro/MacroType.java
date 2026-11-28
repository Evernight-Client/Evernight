package evernight.client.macro;

import evernight.client.modules.Keybind;
import evernight.client.settings.core.NamedEntry;

import java.util.function.BiFunction;

public enum MacroType implements NamedEntry {
    SIMPLE("simple", SimpleMacro::new),
    QUEUE("queue", QueuedMacro::new),
    DOUBLE_TAP("double_tap", DoubleTapMacro::new),
    HOLD("hold", HoldMacro::new);
    private final String name;
    private final BiFunction<String, Keybind, Macro> factory;

    MacroType(String name, BiFunction<String, Keybind, Macro> factory) {
        this.name = name;
        this.factory = factory;
    }

    public static MacroType fromName(String name) {
        for (MacroType type : values()) {
            String typeName = type.getName();
            if (typeName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public Macro create(String name, Keybind keybind) {
        return this.factory.apply(name, keybind);
    }
}