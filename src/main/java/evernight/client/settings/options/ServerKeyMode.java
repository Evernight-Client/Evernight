package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;

/**
 * How waypoints are grouped per server: by the saved server-list name or by the server address.
 */
public enum ServerKeyMode implements NamedEntry {
    ADDRESS("address"),
    NAME("name");

    private final String name;

    ServerKeyMode(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
