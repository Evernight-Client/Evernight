package evernight.client.utils.inventory;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.RotationManager;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Container interaction helpers shared by the automatic mining modules: opening a placed block,
 * closing the managed screen, mapping the open menu and throwing a stack to free a slot.
 */
public final class ContainerInteractionUtils implements ClientAccess {
    private ContainerInteractionUtils() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * Opens the container at the given position. Paths to it first when it is out of interaction
     * range. The caller is responsible for tracking whether the screen is managed by the module.
     */
    public static void openContainer(BlockPos position, boolean rotate) {
        if (position == null || evernightMinecraft.screen != null) {
            return;
        }
        LocalPlayer player = evernightMinecraft.player;
        if (player.blockPosition().distSqr(position) > 9.0D) {
            ClientContext.baritoneBridge.pathTo(position);
            return;
        }
        Vec3 center = position.getCenter();
        Direction face = faceTowards(center);
        Vec3 hitPoint = center.add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        BlockHitResult hitResult = new BlockHitResult(hitPoint, face, position, false);
        if (rotate) {
            float[] angles = RotationManager.calculateRotations(player.getEyePosition(), hitPoint);
            ClientContext.rotationManager.requestRotation(angles, 100);
        }
        PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hitResult);
        PacketSender.sendSwing(InteractionHand.MAIN_HAND);
    }

    public static void closeScreen() {
        LocalPlayer player = evernightMinecraft.player;
        if (player != null && evernightMinecraft.screen != null) {
            player.closeContainer();
        }
    }

    public static void closeForeignScreen(Class<?> expected) {
        Screen screen = evernightMinecraft.screen;
        if (screen != null && !expected.isInstance(screen)) {
            closeScreen();
        }
    }

    public static ContainerInventoryMath.MenuKind menuKind(AbstractContainerMenu menu) {
        if (menu instanceof CraftingMenu) {
            return ContainerInventoryMath.MenuKind.CRAFTING;
        }
        if (menu instanceof InventoryMenu) {
            return ContainerInventoryMath.MenuKind.PLAYER;
        }
        return ContainerInventoryMath.MenuKind.CONTAINER;
    }

    /**
     * Throws one full stack of the first matching item found in the inventory, freeing a slot.
     *
     * @return the dropped item and count, or null when nothing matched
     */
    public static Dropped throwStack(Item... priority) {
        for (Item item : priority) {
            Dropped dropped = throwLargest(stack -> stack.is(item));
            if (dropped != null) {
                return dropped;
            }
        }
        return null;
    }

    private static Dropped throwLargest(Predicate<ItemStack> filter) {
        LocalPlayer player = evernightMinecraft.player;
        if (player == null) {
            return null;
        }
        Inventory inventory = player.getInventory();
        AbstractContainerMenu menu = player.containerMenu;
        ContainerInventoryMath.MenuKind kind = menuKind(menu);
        int bestSlot = -1;
        int bestCount = 0;
        for (int slot = 0; slot < ContainerInventoryMath.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !filter.test(stack)) {
                continue;
            }
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = slot;
            }
        }
        if (bestSlot == -1) {
            return null;
        }
        int menuSlot = ContainerInventoryMath.inventorySlotToMenuSlot(bestSlot, kind);
        if (menuSlot < 0) {
            return null;
        }
        Item item = inventory.getItem(bestSlot).getItem();
        InventoryUtils.clickSlot(menu.containerId, menuSlot, 1, ClickType.THROW);
        return new Dropped(item, bestCount);
    }

    private static Direction faceTowards(Vec3 center) {
        LocalPlayer player = evernightMinecraft.player;
        Vec3 eye = player.getEyePosition();
        double deltaX = eye.x - center.x;
        double deltaY = eye.y - center.y;
        double deltaZ = eye.z - center.z;
        double absoluteX = Math.abs(deltaX);
        double absoluteY = Math.abs(deltaY);
        double absoluteZ = Math.abs(deltaZ);
        if (absoluteX >= absoluteY && absoluteX >= absoluteZ) {
            return deltaX >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (absoluteZ >= absoluteY) {
            return deltaZ >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
        return deltaY >= 0.0D ? Direction.UP : Direction.DOWN;
    }

    public record Dropped(Item item, int count) {
    }
}
