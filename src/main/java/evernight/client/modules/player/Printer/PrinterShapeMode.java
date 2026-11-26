package evernight.client.modules.player.Printer;

import evernight.client.settings.core.NamedEntry;

/**
 * Which part of the placement preview box is drawn.
 *
 * <p>Value names are stable identifiers used by the language table
 * ({@code evernight.names.values.<name>}) and the config file; never rename them after release.
 */
public enum PrinterShapeMode implements NamedEntry {
    BOTH("Both"),
    SIDES("Side"),
    LINES("Line");

    private final String name;

    PrinterShapeMode(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
