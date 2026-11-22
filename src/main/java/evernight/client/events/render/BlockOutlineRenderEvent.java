package evernight.client.events.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import evernight.client.events.core.CancellableEvent;

public final class BlockOutlineRenderEvent extends CancellableEvent {
    private PoseStack poseStack;
    private VertexConsumer vertexConsumer;
    private BlockPos blockPos;
    private BlockState blockState;

    public BlockOutlineRenderEvent(PoseStack poseStack, VertexConsumer vertexConsumer, BlockPos blockPos, BlockState blockState) {
        this.poseStack = poseStack;
        this.vertexConsumer = vertexConsumer;
        this.blockPos = blockPos;
        this.blockState = blockState;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public void setBlockPos(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    public void setBlockState(BlockState blockState) {
        this.blockState = blockState;
    }

    public PoseStack getPoseStack() {
        return this.poseStack;
    }

    public void setPoseStack(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public VertexConsumer getVertexConsumer() {
        return this.vertexConsumer;
    }

    public void setVertexConsumer(VertexConsumer vertexConsumer) {
        this.vertexConsumer = vertexConsumer;
    }
}