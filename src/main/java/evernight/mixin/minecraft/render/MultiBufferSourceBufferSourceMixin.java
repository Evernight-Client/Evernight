package evernight.mixin.minecraft.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import evernight.client.render.mesh.BufferSourceCleanup;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import java.util.SequencedMap;

@Mixin(BufferSource.class)
public class MultiBufferSourceBufferSourceMixin implements BufferSourceCleanup {
    @Shadow
    @Nullable
    public RenderType lastSharedType;
    @Shadow
    @Final
    public Map startedBuilders;
    @Shadow
    @Final
    public SequencedMap fixedBuffers;

    @Unique
    private static void closeBufferMesh(BufferBuilder builder) {
        if (builder != null) {
            MeshData mesh = builder.build();
            if (mesh != null) {
                mesh.close();
            }
        }
    }

    @Override
    public void evernight$cleanAll() {
        if (this.lastSharedType != null) {
            closeBufferMesh((BufferBuilder) this.startedBuilders.remove(this.lastSharedType));
            this.lastSharedType = null;
        }
        for (Object key : this.fixedBuffers.keySet()) {
            RenderType renderType = (RenderType) key;
            BufferBuilder builder = (BufferBuilder) this.startedBuilders.remove(renderType);
            closeBufferMesh(builder);
        }
    }
}