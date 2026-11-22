package evernight.client.events.player;

import net.minecraft.world.item.ItemStack;
import evernight.client.events.core.CancellableEvent;

public final class SlotSetEvent extends CancellableEvent {
    private final ItemStack itemStack;

    public SlotSetEvent(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }
}