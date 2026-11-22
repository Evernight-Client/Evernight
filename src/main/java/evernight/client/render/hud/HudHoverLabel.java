package evernight.client.render.hud;

public class HudHoverLabel {
    private String text;
    private int horizontalDirection;
    private float x;
    private float y;
    private boolean visible;

    public float getY() {
        return this.y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public float getX() {
        return this.x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void show(String text, float x, float y, int direction) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.horizontalDirection = direction;
        this.visible = true;
    }

    public int getHorizontalDirection() {
        return this.horizontalDirection;
    }

    public void setHorizontalDirection(int direction) {
        this.horizontalDirection = direction;
    }
}