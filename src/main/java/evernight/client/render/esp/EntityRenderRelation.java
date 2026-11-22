package evernight.client.render.esp;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import java.util.function.BiConsumer;
import evernight.client.render.mesh.MeshBuffer;

public enum EntityRenderRelation {
    DEFAULT, FRIEND, ENEMY;
    private final MeshBuffer meshBuffer = MeshBuffer.create()
            .setFormat(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

    public static void forEachMesh(BiConsumer consumer) {
        for (EntityRenderRelation relation : values()) {
            consumer.accept(relation, relation.getMeshBuffer());
        }
    }

    public MeshBuffer getMeshBuffer() {
        return this.meshBuffer;
    }
}