package evernight.client.events.player;
import evernight.client.events.core.EventStage;

public final class StepHeightEvent {
    private final EventStage stage;
    private float height;
    private boolean modified;

    public StepHeightEvent(EventStage stage, float height) {
        this.stage = stage;
        this.height = height;
    }

    public EventStage getStage() {
        return this.stage;
    }

    public float getHeight() {
        return this.height;
    }

    public void setHeight(float height) {
        if (this.height != height) {
            this.height = height;
            this.modified = true;
        }
    }

    public boolean isModified() {
        return this.modified;
    }
}