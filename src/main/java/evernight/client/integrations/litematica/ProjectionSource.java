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
     * Whether the given position is inside the render layer range of the active projection.
     *
     * <p>Litematica renders only the configured layer range, so blocks outside of it are invisible
     * to the player and must not be printed. The default keeps every covered position eligible for
     * sources that have no layer concept.
     *
     * @return {@code true} when the position should be treated as part of the rendered projection
     */
    default boolean isWithinRenderLayer(BlockPos pos) {
        return true;
    }

    /**
     * Block state the projection wants at the given position, but only when that position is
     * currently inside the rendered layer range.
     *
     * @return the projected state, or {@code null} when the position is not rendered or no
     * projection covers it
     */
    @Nullable
    default BlockState getProjectedStateIfRendered(BlockPos pos) {
        return this.isWithinRenderLayer(pos) ? this.getProjectedState(pos) : null;
    }

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
