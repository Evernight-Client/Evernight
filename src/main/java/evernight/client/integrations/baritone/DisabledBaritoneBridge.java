package evernight.client.integrations.baritone;

import evernight.client.NamedFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public final class DisabledBaritoneBridge implements BaritoneBridge {
    @Override
    public void pathElytraTo(BlockPos position) {
    }

    @Override
    public void setGoal(BlockPos position) {
    }

    @Override
    public boolean isPathingOrCalculating() {
        return false;
    }

    @Override
    public boolean isInSelection(BlockPos position) {
        return false;
    }

    @Override
    public boolean isPauseRequested() {
        return false;
    }

    @Override
    public BlockPos getGoalPosition() {
        return null;
    }

    @Override
    public void pathTo(BlockPos position) {
    }

    @Override
    public void pathToXZ(int x, int z) {
    }

    @Override
    public void cancelPathing() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void requestPause(NamedFeature feature) {
    }

    @Override
    public void releasePause(NamedFeature feature) {
    }

    @Override
    public void pathForward() {
    }

    @Override
    public void mineBlocks(Block... blocks) {
    }

    @Override
    public void cancelMining() {
    }

    @Override
    public boolean isMining() {
        return false;
    }

    @Override
    public void pickupItems(Predicate<ItemStack> filter) {
    }

    @Override
    public void clearArea(BlockPos firstCorner, BlockPos secondCorner) {
    }

    @Override
    public void placeBlock(BlockPos position, BlockState state) {
    }

    @Override
    public boolean isBuilding() {
        return false;
    }

    @Override
    public void applyMiningProfile(boolean avoidMobs, boolean avoidBlocks, boolean autoTool,
                                   int minY, int maxY) {
    }

    @Override
    public void setMiningYRange(int minY, int maxY) {
    }

    @Override
    public void restoreMiningProfile() {
    }
}