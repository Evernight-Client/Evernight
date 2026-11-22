package evernight.client.utils.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public record BlockRaycastContext(Vec3 start, Vec3 end, Block blockMode, Fluid fluidMode,
                                  CollisionContext collisionContext, BlockRaycastClassifier classifier) {
    public BlockRaycastContext(Vec3 rayStart, Vec3 rayEnd, Block blockSelectionMode, Fluid fluidSelectionMode, Entity collisionEntity, BlockRaycastClassifier overrideClassifier) {
        this(rayStart, rayEnd, blockSelectionMode, fluidSelectionMode, CollisionContext.of(collisionEntity), overrideClassifier);
    }

    public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos blockPos) {
        Block selectionMode = this.blockMode;
        CollisionContext collision = this.collisionContext;
        return selectionMode.get(state, level, blockPos, collision);
    }

    public VoxelShape getFluidShape(FluidState state, BlockGetter level, BlockPos blockPos) {
        Fluid fluidSelectionMode = this.fluidMode;
        VoxelShape selectedShape;
        if (fluidSelectionMode.canPick(state)) {
            selectedShape = state.getShape(level, blockPos);
        } else {
            selectedShape = Shapes.empty();
        }
        return selectedShape;
    }

    public static class Builder {
        private final Vec3 start;
        private final Vec3 end;
        private Block blockMode = Block.COLLIDER;
        private Fluid fluidMode = Fluid.NONE;
        private Entity entity;
        private BlockRaycastClassifier classifier;

        public Builder(Vec3 rayStart, Vec3 rayEnd) {
            this.entity = Minecraft.getInstance().player;
            this.classifier = BlockRaycastClassifier.NO_OVERRIDE;
            this.start = rayStart;
            this.end = rayEnd;
        }

        public Builder withBlockMode(Block blockSelectionMode) {
            this.blockMode = blockSelectionMode;
            return this;
        }

        public Builder withFluidMode(Fluid fluidSelectionMode) {
            this.fluidMode = fluidSelectionMode;
            return this;
        }

        public Builder withEntity(Entity collisionEntity) {
            this.entity = collisionEntity;
            return this;
        }

        public Builder withClassifier(BlockRaycastClassifier overrideClassifier) {
            this.classifier = overrideClassifier;
            return this;
        }

        public Builder withClassifiers(BlockRaycastClassifier... overrideClassifiers) {
            this.classifier = BlockRaycastClassifier.firstNonNone(overrideClassifiers);
            return this;
        }

        public BlockRaycastContext build() {
            return new BlockRaycastContext(this.start, this.end, this.blockMode, this.fluidMode, this.entity,
                    this.classifier);
        }
    }
}