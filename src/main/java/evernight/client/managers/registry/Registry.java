package evernight.client.managers.registry;

import evernight.client.settings.core.NamedEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Registry that rejects duplicate names and keeps insertion order.
 *
 * <p>Entries are compared by {@link evernight.client.NamedFeature#getName()} during
 * registration, which keeps aliases and config names unique without a second index.
 */
public class Registry<T extends NamedEntry> extends AbstractRegistry<T> {
    public Registry() {
        super(new ObjectArrayList<>());
    }

    @Override
    public boolean register(T entry) {
        return this.registerEntry(entry);
    }

    @Override
    public boolean unregister(T entry) {
        return this.unregisterEntry(entry);
    }

    public boolean registerEntry(T entry) {
        if (entry == null) {
            return false;
        }
        for (T registeredEntry : this.entries) {
            if (registeredEntry != null && registeredEntry.getName() != null
                    && registeredEntry.getName().equals(entry.getName())) {
                return false;
            }
        }
        this.entries.add(entry);
        return true;
    }

    public boolean unregisterEntry(T entry) {
        if (entry == null) {
            return false;
        }
        this.entries.remove(entry);
        return true;
    }
}