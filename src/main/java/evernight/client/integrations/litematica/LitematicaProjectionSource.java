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
 * <p>The render layer range is read through {@code DataManager.getRenderLayerRange()}, which returns
 * a MaLiLib {@code LayerRange}. MaLiLib is a hard dependency of Litematica, so this stays safe; the
 * module layer still never sees the MaLiLib type.
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
    public boolean isWithinRenderLayer(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        try {
            return DataManager.getRenderLayerRange().isPositionWithinRange(pos);
        } catch (Throwable failure) {
            return true;
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
