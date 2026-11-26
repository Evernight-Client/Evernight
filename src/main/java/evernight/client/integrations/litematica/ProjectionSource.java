package evernight.client.integrations.litematica;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of the active schematic projection.
 *
 * <p>This is the only type the module layer is allowed to know about. The concrete Litematica
 * implementation lives in {@link LitematicaProjectionSource} and is the single place in the whole
 * repository that references {@code fi.dy.masa.litematica.*}; every other class must go through
 * this interface so a missing optional mod can never trigger {@code NoClassDefFoundError} during
 * module construction (module fields are resolved reflectively by
 * {@code ExperimentalFeatures}). The layout mirrors {@code BaritoneBridge}.
 */
public interface ProjectionSource {
    /**
     * @return whether Litematica is installed at all
     */
    static boolean isLitematicaLoaded() {
        FabricLoader fabric = FabricLoader.getInstance();
        return fabric.isModLoaded("litematica");
    }

    /**
     * Whether a projection can be read right now.
     *
     * <p>Never cache this across ticks: Litematica recreates its schematic world when a schematic is
     * (re)loaded, placed or unplaced, and after joining another dimension or server.
     *
     * @return {@code true} when at least one schematic is loaded and a schematic world exists
     */
    boolean isAvailable();

    /**
     * Block state the projection wants at the given position.
     *
     * @return the projected state, or {@code null} when no projection covers that position
     */
    @Nullable
    BlockState getProjectedState(BlockPos pos);

    /**
     * @return display name of the selected placement, or {@code null} when nothing is selected
     */
    @Nullable
    String getPlacementName();
}
