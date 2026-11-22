package evernight.client.events.ui;
import evernight.client.events.core.CancellableEvent;

public final class MinecraftTickEvent extends CancellableEvent {
    public static final MinecraftTickEvent INSTANCE = new MinecraftTickEvent();

    private MinecraftTickEvent() {
    }
}