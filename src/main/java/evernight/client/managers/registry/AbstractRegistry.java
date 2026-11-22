package evernight.client.managers.registry;

import evernight.client.utils.inventory.RegistryContainer;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Common storage and lookup logic for named registries.
 *
 * <p>The backing {@link List} is supplied by the subclass so registries can pick the iteration
 * characteristics they need; {@link #findFirst} and {@link #findFirstOrNull} provide null-safe lookups
 * without exposing the list order.
 */
public abstract class AbstractRegistry<T> implements RegistryContainer<T> {
    protected final List<T> entries;

    protected AbstractRegistry(List<T> entries) {
        this.entries = entries;
    }

    @Override
    public List<T> getRegistry() {
        return this.entries;
    }

    public Optional<T> findFirst(Predicate<? super T> predicate) {
        for (T entry : this.entries) {
            if (predicate.test(entry)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public T findFirstOrNull(Predicate<? super T> predicate) {
        for (T entry : this.entries) {
            if (predicate.test(entry)) {
                return entry;
            }
        }
        return null;
    }
}