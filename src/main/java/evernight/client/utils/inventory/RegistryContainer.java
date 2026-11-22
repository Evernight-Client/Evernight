package evernight.client.utils.inventory;

import java.util.Collection;

/**
 * A named collection whose entries can be registered and unregistered.
 *
 * <p>Implemented by the {@code managers} registries as well as by {@code Module} and {@code Panel},
 * which expose their settings and child elements through the same contract.
 *
 * @param <T> type of the registered entries
 */
public interface RegistryContainer<T> {
    boolean register(T entry);

    boolean unregister(T entry);

    Collection<T> getRegistry();
}