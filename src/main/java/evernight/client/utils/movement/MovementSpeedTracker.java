package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.utils.core.ElapsedTimer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;

public class MovementSpeedTracker implements ClientAccess {
    private final ArrayDeque<Double> speedSamples = new ArrayDeque<>();
    private final ElapsedTimer displayTimer = new ElapsedTimer();
    private double averageSpeed;
    private double displaySpeed;
    private double currentSpeed;

    public MovementSpeedTracker() {
        ClientAccess.eventBus.subscribe(this);
    }

    public double getAverageSpeed() {
        return this.averageSpeed;
    }

    public double getCurrentSpeed() {
        return this.currentSpeed;
    }

    public double getDisplaySpeed() {
        return this.displaySpeed;
    }

    public double calculateCurrentSpeed() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Entity entity = player.isPassenger() ? player.getVehicle() : player;
        return Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) * 20.0D * 3.6D;
    }

    @EventHandler
    private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        this.averageSpeed = 0.0;
        this.displaySpeed = 0.0;
        this.speedSamples.clear();
        this.displayTimer.reset();
    }

    @EventHandler
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        if (!event.hasPendingTicks() || this.isWorldUnavailable()) {
            return;
        }
        this.currentSpeed = this.calculateCurrentSpeed();
        if (this.currentSpeed > 0.0 || ClientAccess.evernightMinecraft.player.tickCount % 4 == 0) {
            this.speedSamples.add(this.currentSpeed * ClientContext.tickRateController.getMultiplier());
        } else {
            this.speedSamples.pollFirst();
        }
        while (this.speedSamples.size() > 9) {
            this.speedSamples.pollFirst();
        }
        double total = 0.0;
        for (double speed : this.speedSamples) {
            total += speed;
        }
        this.averageSpeed = this.speedSamples.isEmpty() ? 0.0 : total / this.speedSamples.size();
        if (this.averageSpeed < 21.0D) {
            this.displaySpeed = this.averageSpeed;
        } else if (this.displayTimer.hasElapsed(50L)) {
            this.displaySpeed = this.averageSpeed;
            this.displayTimer.reset();
        }
    }
}