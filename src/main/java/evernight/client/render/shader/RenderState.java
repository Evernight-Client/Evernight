package evernight.client.render.shader;

public interface RenderState {
    static RenderState combine(RenderState... states) {
        return entering -> {
            for (RenderState state : states) {
                state.apply(entering);
            }
        };
    }

    default RenderState then(RenderState next) {
        return entering -> {
            this.apply(entering);
            next.apply(entering);
        };
    }

    void apply(boolean entering);
}