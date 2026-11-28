package evernight.client.modules.hud.Welcomer;

import java.awt.*;

public class HudNotification {
    protected final Color color;
    protected String text;
    protected long startTimeMillis;
    protected long endTimeMillis;

    public HudNotification(String text, long durationMillis, Color color) {
        this.text = text;
        this.color = color;
        this.startTimeMillis = System.currentTimeMillis();
        this.endTimeMillis = this.startTimeMillis + durationMillis;
    }

    public long getStartTimeMillis() {
        return this.startTimeMillis;
    }

    public String getText() {
        return this.text;
    }

    public long getEndTimeMillis() {
        return this.endTimeMillis;
    }

    public Color getColor() {
        return this.color;
    }

    public void update() {
    }
}