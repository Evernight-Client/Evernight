package evernight.client.utils.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects the drops of one ominous vault opening from the client packets.
 *
 * <p>Packet order matters: when a vault switches to {@code EJECTING} the opening is tracked, every
 * item entity spawned inside the vault's inflated box is remembered, and each set-entity-data packet
 * for one of those ids appends the stack. The list therefore follows the ejection order, which the
 * prediction reverses before comparing it with the Java core output. Polling entities per tick or
 * sorting by id would silently reorder the drops and break detection, so do not replace this with a
 * tick loop.
 *
 * <p>The three entry points are called from {@code VaultLootCaptureMixin} on the client thread, after
 * vanilla has applied the packet.
 */
public final class VaultLootCapture {
    /** One finished ejection: the item stacks in packet arrival order plus whether it was ominous. */
    public record Capture(List<ItemStack> items, boolean ominous) {
    }

    /** Receives one finished ejection on the client thread. */
    public interface Listener {
        void onCapture(Capture capture);
    }

    private static Listener listener;
    private static BlockPos ejectingPosition;
    private static boolean ominous;
    private static final Set<Integer> pendingEntityIds = new LinkedHashSet<>();
    private static final List<ItemStack> ejectedItems = new ArrayList<>();

    private VaultLootCapture() {
    }

    public static void setListener(Listener value) {
        listener = value;
        reset();
    }

    public static boolean hasListener() {
        return listener != null;
    }

    public static void clear() {
        listener = null;
        reset();
    }

    private static void reset() {
        ejectingPosition = null;
        pendingEntityIds.clear();
        ejectedItems.clear();
    }

    /** A block update changed a vault's state. */
    public static void onBlockUpdate(BlockPos position, BlockState state) {
        if (listener == null) {
            return;
        }
        Optional<VaultState> optionalState = state.getOptionalValue(VaultBlock.STATE);
        if (optionalState.isEmpty()) {
            return;
        }
        boolean ominousVault = state.getValueOrElse(VaultBlock.OMINOUS, false);
        switch (optionalState.get()) {
            case EJECTING -> {
                if (ejectingPosition == null) {
                    ejectingPosition = position;
                    ominous = ominousVault;
                }
            }
            case ACTIVE, INACTIVE -> {
                if (ejectingPosition != null && ejectingPosition.equals(position)) {
                    ejectingPosition = null;
                    // Wait until every remembered item id has delivered its data packet.
                    if (pendingEntityIds.isEmpty()) {
                        if (!ejectedItems.isEmpty()) {
                            listener.onCapture(new Capture(List.copyOf(ejectedItems), ominous));
                        }
                        reset();
                    }
                }
            }
        }
    }

    /** An entity spawned; item entities inside the ejecting vault are tracked. */
    public static void onEntitySpawn(int entityId, EntityType<?> type, double x, double y, double z) {
        if (listener == null || ejectingPosition == null) {
            return;
        }
        if (type != EntityType.ITEM) {
            return;
        }
        if (new AABB(ejectingPosition).inflate(1.0).contains(x, y, z)) {
            pendingEntityIds.add(entityId);
        }
    }

    /** Entity data arrived; the stack of a tracked item entity becomes one drop in arrival order. */
    public static void onEntityData(int entityId, ItemEntity entity) {
        if (listener == null) {
            return;
        }
        if (pendingEntityIds.remove(entityId)) {
            ejectedItems.add(entity.getItem().copy());
        }
    }
}
