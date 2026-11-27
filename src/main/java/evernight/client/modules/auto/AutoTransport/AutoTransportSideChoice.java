package evernight.client.modules.auto.AutoTransport;

import evernight.client.settings.core.NamedEntry;

/**
 * Which end of the route the module starts on.
 *
 * <p>{@link #AUTO} follows the recorded throw points and flips sides after every teleport; the two
 * explicit values lock the module to one side for manual testing. {@link #getName()} is the stable
 * identifier used by the config and the preset payload.
 */
public enum AutoTransportSideChoice implements NamedEntry {
    AUTO("Auto"),
    SOURCE("Source"),
    DESTINATION("Destination");

    private final String displayName;

    AutoTransportSideChoice(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}
