package evernight.client.render.mesh;

import com.mojang.blaze3d.vertex.VertexConsumer;
import evernight.client.utils.inventory.NoOpVertexConsumer;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

public class NoOpOutlineBufferSource extends OutlineBufferSource {
    public static final NoOpOutlineBufferSource INSTANCE = new NoOpOutlineBufferSource();

    private NoOpOutlineBufferSource() {
    }

    @Override
    public void endOutlineBatch() {
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return NoOpVertexConsumer.INSTANCE;
    }
}