package evernight.client.managers.player;

import evernight.client.ClientAccess;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.movement.PlayerMotionPredictor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.MinecraftDisconnectEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.ui.MinecraftRunTickEvent;
import evernight.client.events.world.ClientLevelRemoveEntityEvent;

public final class MovementPredictionManager implements ClientAccess {
    private final Map<Integer, PlayerMotionPredictor> playerPredictions = Collections.synchronizedMap(new HashMap<>());
    private final ElapsedTimer predictionTimer = new ElapsedTimer();
    private Vec3 previousPlayerPosition;

    public MovementPredictionManager() {
        ClientAccess.eventBus.subscribe(this);
    }

    public Vec3 getAdjustedLocalMotion() {
        if (this.previousPlayerPosition == null) {
            return Vec3.ZERO;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 motion = player.position().subtract(this.previousPlayerPosition);
        if (player.isSprinting() && !player.isFallFlying()) {
            motion = motion.multiply(0.9F, 1.0D, 0.9F);
        }
        return motion;
    }

    public Vec3 getPredictedEyePosition() {
        return ClientAccess.evernightMinecraft.player.getEyePosition().add(this.getAdjustedLocalMotion());
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.previousPlayerPosition = new Vec3(ClientAccess.evernightMinecraft.player.xo,
                ClientAccess.evernightMinecraft.player.yo, ClientAccess.evernightMinecraft.player.zo);
    }

    @EventHandler
    private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
        if (this.isWorldUnavailable() || !this.predictionTimer.hasElapsedAndReset(25L)) {
            return;
        }
        for (AbstractClientPlayer player : ClientAccess.evernightMinecraft.level.players()) {
            this.playerPredictions.computeIfAbsent(player.getId(), ignored -> new PlayerMotionPredictor(player))
                    .updatePredictions();
        }
    }

    @EventHandler
    private void onMinecraftDisconnectEvent(MinecraftDisconnectEvent event) {
        this.playerPredictions.clear();
    }

    public synchronized AABB getPredictedBoundingBox(Player player, int tickIndex) {
        PlayerMotionPredictor prediction = this.playerPredictions.get(player.getId());
        if (prediction == null) {
            return player.getBoundingBox();
        }
        List<AABB> positions = prediction.getPredictedBoxes();
        return positions.isEmpty() ? player.getBoundingBox() : positions.get(Math.min(tickIndex, positions.size() - 1));
    }

    @EventHandler
    private void onClientLevelRemoveEntityEvent(ClientLevelRemoveEntityEvent event) {
        synchronized (this.playerPredictions) {
            this.playerPredictions.remove(event.getId());
        }
    }
}