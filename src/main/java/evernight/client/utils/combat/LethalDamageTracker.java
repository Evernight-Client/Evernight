package evernight.client.utils.combat;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.DamageUtils;
import evernight.client.utils.entity.EntityUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

public final class LethalDamageTracker implements ClientAccess {
    private final ElapsedTimer lethalCrystalTimer = new ElapsedTimer();
    private final LethalCheckPolicy policy;

    public LethalDamageTracker(LethalCheckPolicy checkPolicy) {
        this.policy = checkPolicy;
    }

    public void update() {
        if (hasLethalCrystal()) {
            this.lethalCrystalTimer.reset();
        }
    }

    public boolean isLethalCheckEnabled() {
        return this.policy.isLethalCheckEnabled();
    }

    public boolean isCrystalLethal(Vec3 crystalPosition) {
        double squaredDistance = crystalPosition.distanceToSqr(ClientAccess.evernightMinecraft.player.position());
        if (squaredDistance > 144.0D) {
            return false;
        }
        for (int predictionStep = 4; predictionStep >= 0; predictionStep--) {
            AABB predictedPlayerBox = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.evernightMinecraft.player, predictionStep);
            LocalPlayer playerForDamageCheck = ClientAccess.evernightMinecraft.player;
            double predictedDamage = DamageUtils.getCrystalDamage(crystalPosition, playerForDamageCheck, predictedPlayerBox);
            if (predictedDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearbyCrystal(Entity entity) {
        if (entity instanceof EndCrystal) {
            double squaredDistance = ClientAccess.evernightMinecraft.player.distanceToSqr(entity);
            return squaredDistance <= 144.0D;
        }
        return false;
    }

    public boolean shouldHoldTotem() {
        return isLethalCheckEnabled() && !this.lethalCrystalTimer.hasElapsed(750L);
    }

    public boolean hasLethalCrystal() {
        synchronized (ClientAccess.evernightMinecraft.level.entitiesForRendering()) {
            Iterator<Entity> entityIterator = ClientAccess.evernightMinecraft.level.entitiesForRendering().iterator();
            while (entityIterator.hasNext()) {
                Entity entity = entityIterator.next();
                if (isNearbyCrystal(entity)) {
                    if (isCrystalLethal(entity.position())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public interface LethalCheckPolicy {
        boolean isLethalCheckEnabled();
    }
}