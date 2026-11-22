package evernight.client.gui.components;

import evernight.client.gui.panels.Panel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;
import evernight.client.gui.components.base.AbstractItemListComponent;

public class ItemListComponent extends AbstractItemListComponent<Item> {
    public ItemListComponent(Panel panel, Predicate<Item> filter) {
        super(panel, filter, BuiltInRegistries.ITEM.size());
    }

    @Override
    public void update(double mouseX, double mouseY) {
        this.entries.clear();
        BuiltInRegistries.ITEM.stream().filter(this.filter).forEach(this.entries::add);
    }

    @Override
    public String getEntryLabel(Item item) {
        return item.getName().getString();
    }

    @Override
    public ItemStack getEntryIcon(Item item) {
        return item.getDefaultInstance();
    }
}