package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.network.PacketSender;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * Experience bottle throwing shared by AutoExp and the other mending helpers.
 *
 * <p>The throw itself is a normal {@code ServerboundUseItemPacket}. What makes it a "ghost hand"
 * throw is the silent hotbar swap: the bottle is moved into the hand for the use packet and the
 * previous slot is restored immediately afterwards, so the tool is already back in the main hand when
 * the experience orbs are absorbed and Mending applies to it.
 */
public final class ExperienceBottleUtils implements ClientAccess {
    private ExperienceBottleUtils() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    public static int countBottles() {
        return InventoryUtils.countItems(stack -> stack.is(Items.EXPERIENCE_BOTTLE));
    }

    /**
     * Throws {@code count} experience bottles.
     *
     * @param hand     hand to use when no source slot is known (e.g. bottles were already moved into
     *                 the selected slot by the caller)
     * @param source   bottle slot found by the caller, may be null / not found
     * @param count    number of bottles to throw
     * @param rotation yaw/pitch to put into the use packet, or null to use the client rotation
     * @param silent   whether the hand swap is undone immediately (ghost hand mode)
     * @return whether a throw was sent
     */
    public static boolean throwBottles(InteractionHand hand, InventoryUtils.SwapSlot source, int count,
                                       float[] rotation, boolean silent) {
        if (count <= 0) {
            return false;
        }
        if (evernightMinecraft.player == null) {
            return false;
        }
        int previousSlot = InventoryUtils.getSelectedSlot();
        boolean swapped = false;
        InteractionHand useHand = hand;
        if (source != null && source.isFound()) {
            useHand = source.hand();
            if (!source.isInHand()) {
                InventoryUtils.swapTo(source);
                swapped = true;
            }
        }
        if (rotation != null && rotation.length >= 2) {
            for (int thrown = 0; thrown < count; thrown++) {
                PacketSender.sendUseItem(useHand, rotation[0], rotation[1]);
            }
        } else {
            for (int thrown = 0; thrown < count; thrown++) {
                PacketSender.sendUseItem(useHand);
            }
        }
        ClientContext.swingManager.swingPacket(useHand, SwingAction.INTERACT);
        if (swapped && silent) {
            InventoryUtils.swapBack(previousSlot, source);
        }
        return true;
    }

}
