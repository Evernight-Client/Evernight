package evernight.client.events.render;

import net.minecraft.world.entity.Entity;

import java.awt.*;
import evernight.client.events.core.CancellableEvent;

public final class LivingEntityModelSubmitEvent extends CancellableEvent {
    private static final LivingEntityModelSubmitEvent INSTANCE = new LivingEntityModelSubmitEvent();
    private Entity entity;
    private int lightCoords;
    private float red;
    private float green;
    private float blue;
    private float alpha;

    public static LivingEntityModelSubmitEvent create(
            Entity entity, int lightCoords, float red, float green, float blue, float alpha) {
        INSTANCE.lightCoords = lightCoords;
        INSTANCE.entity = entity;
        INSTANCE.red = red;
        INSTANCE.green = green;
        INSTANCE.blue = blue;
        INSTANCE.alpha = alpha;
        INSTANCE.resetCancellation();
        return INSTANCE;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public float getAlpha() {
        return this.alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public float getRed() {
        return this.red;
    }

    public void setRed(float red) {
        this.red = red;
    }

    public float getGreen() {
        return this.green;
    }

    public void setGreen(float green) {
        this.green = green;
    }

    public float getBlue() {
        return this.blue;
    }

    public void setBlue(float blue) {
        this.blue = blue;
    }

    public int getLightCoords() {
        return this.lightCoords;
    }

    public void setLightCoords(int lightCoords) {
        this.lightCoords = lightCoords;
    }

    public void setColor(Color color) {
        this.setRed(color.getRed() / 255.0F);
        this.setGreen(color.getGreen() / 255.0F);
        this.setBlue(color.getBlue() / 255.0F);
        this.setAlpha(color.getAlpha() / 255.0F);
    }
}