package evernight.client.utils.core;

import evernight.client.ClientContext;
import evernight.client.utils.math.MathConstants;

import java.util.function.Supplier;

public class InterpolatedFloat {
    public float currentValue;
    public float targetValue;
    public float previousValue;
    protected boolean easingEnabled;
    protected Supplier speedSupplier;

    public InterpolatedFloat(float speed, boolean easingEnabled) {
        this.speedSupplier = () -> speed;
        this.easingEnabled = easingEnabled;
    }

    public InterpolatedFloat(Supplier speedSupplier, boolean easingEnabled) {
        this.speedSupplier = speedSupplier;
        this.easingEnabled = easingEnabled;
    }

    public InterpolatedFloat(float speed) {
        this(speed, true);
    }

    public float getValue() {
        return this.currentValue;
    }

    public void setValue(float value) {
        if (this.currentValue != value) {
            this.previousValue = value;
            this.currentValue = value;
            this.targetValue = value;
        }
    }

    public void setTarget(float value) {
        if (this.currentValue != value) {
            this.previousValue = this.currentValue;
            this.currentValue = value;
            this.targetValue = value;
        }
    }

    public float updateAndGet() {
        FrameTiming frameTiming = ClientContext.frameTiming;
        float frameArgument = 0.5F;
        float frameFactor = frameTiming.getFrameFactor(frameArgument);
        Float speedValue = (Float) this.speedSupplier.get();
        float interpolationFactor = frameFactor * speedValue;
        if (this.easingEnabled) {
            float factorOffset = interpolationFactor - 1.0F;
            interpolationFactor = (float) (1.0D - (double) factorOffset * Math.pow(factorOffset, 3.0D));
        }
        this.currentValue = this.previousValue + (this.currentValue - this.previousValue) * interpolationFactor;
        boolean shouldSnap = this.currentValue == this.targetValue;
        if (!shouldSnap) {
            float remainingDifference = this.targetValue - this.currentValue;
            shouldSnap = Math.abs(remainingDifference) < MathConstants.HALF;
        }
        if (shouldSnap) {
            float snapValue = this.targetValue;
            this.setValue(snapValue);
        }
        int frameLimit = ClientContext.frameTiming.getFramesPerSecond();
        if (frameLimit <= 15) {
            this.currentValue = this.targetValue;
        }
        return this.currentValue;
    }

    public void setEasingEnabled(boolean easingEnabled) {
        this.easingEnabled = easingEnabled;
    }

    public Supplier getSpeedSupplier() {
        return this.speedSupplier;
    }

    public void setSpeedSupplier(Supplier speedSupplier) {
        this.speedSupplier = speedSupplier;
    }
}