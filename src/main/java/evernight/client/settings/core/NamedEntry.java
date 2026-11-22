package evernight.client.settings.core;

import evernight.client.render.text.Translations;

import net.minecraft.network.chat.Component;

public interface NamedEntry {
    String getName();

    default NamedEntry withName(String name) {
        return () -> name;
    }

    /**
     * Label shown by the interface. The raw {@link #getName()} stays the config/serialisation name,
     * so translated labels never leak into saved configs or command arguments.
     */
    default String getDisplayName() {
        return Translations.value(this.getName());
    }

    default Component getDisplayComponent() {
        return Component.literal(this.getName());
    }
}