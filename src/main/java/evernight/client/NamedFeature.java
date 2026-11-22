package evernight.client;

import evernight.client.settings.core.NamedEntry;

import java.util.Objects;

public class NamedFeature implements NamedEntry {
    private final String name;

    public NamedFeature(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            NamedFeature feature = (NamedFeature) other;
            return Objects.equals(this.name, feature.name);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

}