package evernight.client.commands.control.Action;

import evernight.client.ClientAccess;
import net.minecraft.world.item.Item;

public interface ActionSwapStrategy extends ClientAccess {
    SwapSlots findSlots(Item[] items);

    void apply(SwapSlots slots);

    void undo(SwapSlots slots);
}