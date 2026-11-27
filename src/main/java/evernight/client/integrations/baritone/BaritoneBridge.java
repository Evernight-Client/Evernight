package evernight.client.integrations.baritone;

import evernight.client.NamedFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public interface BaritoneBridge {
    /**
     * Baritone ships under different mod ids depending on the distribution: Meteor's build is
     * {@code baritone-meteor} while the rfresh2 fork keeps {@code baritone}. Both provide the same
     * {@code baritone.*} packages and API.
     *
     * @return whether any supported Baritone distribution is installed
     */
    static boolean isBaritoneLoaded() {
        FabricLoader fabric = FabricLoader.getInstance();
        return fabric.isModLoaded("baritone") || fabric.isModLoaded("baritone-meteor");
    }

    void pathElytraTo(BlockPos position);

    void setGoal(BlockPos position);

    boolean isPathingOrCalculating();

    boolean isInSelection(BlockPos position);

    boolean isPauseRequested();

    BlockPos getGoalPosition();

    void pathTo(BlockPos position);

    /**
     * Paths to the given XZ coordinate without requiring an exact Y, used to walk out of the deep dark
     * where the terrain height is unknown.
     */
    void pathToXZ(int x, int z);

    void cancelPathing();

    boolean isAvailable();

    void requestPause(NamedFeature feature);

    void releasePause(NamedFeature feature);

    void pathForward();

    /**
     * Starts Baritone's mine process for the given blocks.
     *
     * <p>Calling this repeatedly while the process is running only refreshes its target filter, so
     * callers should gate it on {@link #isMining()}.
     */
    void mineBlocks(Block... blocks);

    /**
     * Cancels the mine and follow processes. Pathing is cancelled separately by
     * {@link #cancelPathing()}.
     */
    void cancelMining();

    /**
     * @return whether Baritone's mine process is currently active
     */
    boolean isMining();

    /**
     * Makes Baritone walk to and pick up dropped items matching the filter.
     */
    void pickupItems(Predicate<ItemStack> filter);

    /**
     * Makes Baritone clear every block between the two corners, used to carve the storage pocket and to
     * break a single placed block again.
     */
    void clearArea(BlockPos firstCorner, BlockPos secondCorner);

    /**
     * Makes Baritone place a single block at the position: it paths there, takes the item from the
     * hotbar and places it.
     */
    void placeBlock(BlockPos position, BlockState state);

    /**
     * @return whether Baritone's builder process is currently active
     */
    boolean isBuilding();

    /**
     * Applies the settings the auto miner depends on and remembers their previous values so
     * {@link #restoreMiningProfile()} can undo them.
     *
     * <p>{@code minY} and {@code maxY} are absolute world Y levels; implementations translate them to
     * whatever representation the pathfinder uses internally.
     *
     * <p>Values are only remembered once, so re-applying a profile never overwrites the snapshot with
     * already modified values.
     */
    void applyMiningProfile(boolean avoidMobs, boolean avoidBlocks, boolean autoTool, int minY, int maxY);

    /**
     * Updates the absolute world Y band Baritone may mine in, remembering the previous values like
     * {@link #applyMiningProfile}.
     */
    void setMiningYRange(int minY, int maxY);

    /**
     * Restores every Baritone setting touched by {@link #applyMiningProfile} and
     * {@link #setMiningYRange}.
     */
    void restoreMiningProfile();

    /**
     * Stops Baritone from breaking or placing blocks while one of the travel bots (the pearl bot and
     * the cargo transport) walks, so the route neither digs through nor builds over the base.
     *
     * <p>The restriction is reference counted: every call must be paired with
     * {@link #releaseNoBreakPlace()}, and the values that were active before the first request are
     * restored when the last holder releases.
     */
    void requestNoBreakPlace();

    /**
     * Releases one {@link #requestNoBreakPlace()} hold; the last release restores the previous
     * {@code allowBreak}/{@code allowPlace} values. A call without a matching request is ignored.
     */
    void releaseNoBreakPlace();
}