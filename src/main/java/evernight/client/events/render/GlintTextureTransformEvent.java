package evernight.client.events.render;
import evernight.client.events.core.CancellableEvent;

public final class GlintTextureTransformEvent extends CancellableEvent {
    private static final GlintTextureTransformEvent INSTANCE = new GlintTextureTransformEvent();
    private float scale = 1.0F;

    public static GlintTextureTransformEvent resetAndGet() {
        INSTANCE.setScale(1.0F);
        return INSTANCE;
    }

    public float getScale() {
        return this.scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }
}