package evernight.client.events.render;

import net.minecraft.client.model.EntityModel;
import evernight.client.events.core.CancellableEvent;
import evernight.client.events.core.EventStage;

public final class ModelFeatureRendererRenderModelEvent extends CancellableEvent {
    private EntityModel model;
    private EventStage stage;

    public ModelFeatureRendererRenderModelEvent(EntityModel model, EventStage stage) {
        this.model = model;
        this.stage = stage;
    }

    public EventStage getStage() {
        return this.stage;
    }

    public void setStage(EventStage stage) {
        this.stage = stage;
    }

    public EntityModel getModel() {
        return this.model;
    }

    public void setModel(EntityModel model) {
        this.model = model;
    }
}