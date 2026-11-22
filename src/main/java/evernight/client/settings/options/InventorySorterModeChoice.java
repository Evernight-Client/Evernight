package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.inventory.InventorySortStrategy;

public enum InventorySorterModeChoice implements NamedEntry {
    TYPE("Type", ComparatorInventorySortStrategy.BY_TYPE),
    NAME("Name", ComparatorInventorySortStrategy.BY_NAME),
    KIT("Kit", KitInventorySortStrategy.INSTANCE);
    private final String displayName;
    private final InventorySortStrategy strategy;

    InventorySorterModeChoice(String displayName, InventorySortStrategy strategy) {
        this.displayName = displayName;
        this.strategy = strategy;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public InventorySortStrategy getStrategy() {
        return this.strategy;
    }
}