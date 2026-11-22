package evernight.client.settings.options;

import evernight.client.settings.core.NamedEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum AntiPhaseMaterialChoice implements NamedEntry {
    LADDER("Ladder", Items.LADDER),
    VINE("Vine", Items.VINE),
    SCAFFOLDING("Scaffolding", Items.SCAFFOLDING),
    ITEMFRAME("ItemFrame", Items.ITEM_FRAME);
    private final String displayName;
    private final Item item;

    AntiPhaseMaterialChoice(String displayName, Item item) {
        this.displayName = displayName;
        this.item = item;
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public Item getItem() {
        return this.item;
    }
}