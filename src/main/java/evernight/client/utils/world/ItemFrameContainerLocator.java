package evernight.client.utils.world;

import evernight.client.ClientAccess;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Locates item frames that display a marker item, and the containers those frames are attached to.
 *
 * <p>A frame is a marker when the item it displays matches the caller's filter, for example an ender
 * pearl marking the pearl chest, a shulker box marking the cargo chest, or a renamed redstone item
 * marking one end of a transport route. The marked container is the block the frame is attached to,
 * which vanilla derives from the frame position and its facing direction; only block entities
 * implementing {@link Container} are reported (chests, barrels, shulker boxes, hoppers, dispensers and
 * similar), so frames attached to walls or signs are ignored.
 *
 * <p>All methods are read-only queries over the currently loaded world and return an empty result
 * when there is no local player or level. Results are ordered by distance to the player, nearest
 * first, so callers can simply take the first entry.
 */
public final class ItemFrameContainerLocator implements ClientAccess {
    private ItemFrameContainerLocator() {
        throw new IllegalArgumentException("Illegal constructor call");
    }

    /**
     * @return every loaded item frame whose displayed item matches the filter, nearest first
     */
    public static List<ItemFrame> findMarkedFrames(Predicate<ItemStack> marker) {
        if (marker == null) {
            return List.of();
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        if (level == null || ClientAccess.evernightMinecraft.player == null) {
            return List.of();
        }
        List<ItemFrame> frames = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            ItemStack displayed = frame.getItem();
            if (displayed.isEmpty() || !marker.test(displayed)) {
                continue;
            }
            frames.add(frame);
        }
        if (frames.size() > 1) {
            Vec3 eyePosition = ClientAccess.evernightMinecraft.player.position();
            frames.sort(Comparator.comparingDouble(frame -> frame.position().distanceToSqr(eyePosition)));
        }
        return frames;
    }

    /**
     * @return the nearest marked frame within the given distance, or empty when none matches
     */
    public static Optional<ItemFrame> findNearestFrame(Predicate<ItemStack> marker, double maxDistance) {
        if (ClientAccess.evernightMinecraft.player == null) {
            return Optional.empty();
        }
        Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
        for (ItemFrame frame : findMarkedFrames(marker)) {
            if (playerPosition.distanceTo(frame.position()) <= maxDistance) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    /**
     * @return every container marked by a matching frame, nearest first and without duplicates
     */
    public static List<BlockPos> findAllContainers(Predicate<ItemStack> marker) {
        List<BlockPos> containers = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (ItemFrame frame : findMarkedFrames(marker)) {
            resolveContainer(frame).ifPresent(position -> {
                if (seen.add(position)) {
                    containers.add(position);
                }
            });
        }
        return containers;
    }

    /**
     * @return the nearest marked container within the given distance, or empty when none matches
     */
    public static Optional<BlockPos> findNearestContainer(Predicate<ItemStack> marker, double maxDistance) {
        if (ClientAccess.evernightMinecraft.player == null) {
            return Optional.empty();
        }
        Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
        for (BlockPos candidate : findAllContainers(marker)) {
            if (playerPosition.distanceTo(candidate.getCenter()) <= maxDistance) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Convenience overload for callers that mark a container with a single item type.
     */
    public static Optional<BlockPos> findNearestContainer(Item markerItem, double maxDistance) {
        if (markerItem == null) {
            return Optional.empty();
        }
        return findNearestContainer(stack -> stack.is(markerItem), maxDistance);
    }

    /**
     * Resolves the container a frame is attached to.
     *
     * @return the support block when it holds a container, empty for frames on non-container blocks
     */
    public static Optional<BlockPos> resolveContainer(ItemFrame frame) {
        if (frame == null) {
            return Optional.empty();
        }
        BlockPos supportPosition = frame.getPos().relative(frame.getDirection().getOpposite());
        return isContainer(supportPosition) ? Optional.of(supportPosition) : Optional.empty();
    }

    /**
     * @return whether the block at the position holds a container block entity
     */
    public static boolean isContainer(BlockPos position) {
        if (position == null) {
            return false;
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        return level != null && level.getBlockEntity(position) instanceof Container;
    }
}
