package evernight.client.integrations.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Litematica-backed projection source.
 *
 * <p>Only static entry points are touched on purpose ({@code SchematicWorldHandler} and
 * {@code DataManager}), so no Litematica object is ever stored in a field of an Evernight class:
 * the schematic world is rebuilt by Litematica whenever a schematic is loaded, placed or unplaced
 * and whenever the client changes dimension, so every call re-reads it.
 *
 * <p>Deliberately does <b>not</b> use {@code DataManager.getRenderLayerRange()}: that returns a
 * MaLiLib {@code LayerRange} and would pull MaLiLib types into the module layer. The scan radius is
 * owned by the module instead.
 */
public final class LitematicaProjectionSource implements ProjectionSource {
    @Override
    public boolean isAvailable() {
        try {
            WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
            return world != null && getPlacementName() != null;
        } catch (Throwable failure) {
            return false;
        }
    }

    @Override
    public @Nullable BlockState getProjectedState(BlockPos pos) {
        try {
            WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
            if (world == null || pos == null) {
                return null;
            }
            return world.getBlockState(pos);
        } catch (Throwable failure) {
            return null;
        }
    }

    @Override
    public @Nullable String getPlacementName() {
        try {
            SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
            return placement == null ? null : placement.getName();
        } catch (Throwable failure) {
            return null;
        }
    }
}
