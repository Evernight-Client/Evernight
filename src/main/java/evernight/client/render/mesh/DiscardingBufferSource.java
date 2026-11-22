package evernight.client.render.mesh;

import com.mojang.blaze3d.vertex.VertexConsumer;
import evernight.client.utils.inventory.NoOpVertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

public class DiscardingBufferSource extends BufferSource {
    public static final DiscardingBufferSource INSTANCE = new DiscardingBufferSource();

    private DiscardingBufferSource() {
        super(null, null);
    }

    @Override
    public void endBatch(RenderType renderType) {
    }

    @Override
    public void endBatch() {
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return NoOpVertexConsumer.INSTANCE;
    }
}