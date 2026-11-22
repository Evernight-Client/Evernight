package evernight.client.events.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import evernight.client.events.core.CancellableEvent;
import evernight.client.events.core.EventStage;

public final class RenderTypeDrawEvent extends CancellableEvent {
    private final EventStage stage;
    private GpuTextureView override;

    public RenderTypeDrawEvent(EventStage stage) {
        this.stage = stage;
    }

    public EventStage getStage() {
        return this.stage;
    }

    public GpuTextureView getOverride() {
        return this.override;
    }

    public void setOverride(GpuTextureView override) {
        this.override = override;
    }
}