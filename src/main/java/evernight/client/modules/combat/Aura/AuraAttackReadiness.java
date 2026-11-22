package evernight.client.modules.combat.Aura;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.movement.Flight.FlightModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AntiCheatRotationsChoice;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.entity.PlayerStateUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import evernight.client.modules.combat.Criticals.CriticalsModule;

public final class AuraAttackReadiness implements ClientAccess {
    private static AntiCheatModule antiCheat;
    private static FlightModule flight;
    private static CriticalsModule criticals;

    static {
        antiCheat = ClientContext.getModule(AntiCheatModule.class);
        flight = ClientContext.getModule(FlightModule.class);
        criticals = ClientContext.getModule(CriticalsModule.class);
    }

    private final AuraModule aura;

    public AuraAttackReadiness(AuraModule aura) {
        this.aura = aura;
    }

    public static void bindModuleRegistry(ModuleRegistry modules) {
        antiCheat = modules.getModule(AntiCheatModule.class);
        flight = modules.getModule(FlightModule.class);
        criticals = modules.getModule(CriticalsModule.class);
    }

    private boolean shouldWaitForCriticalMovement() {
        boolean onGround = ClientAccess.evernightMinecraft.player.onGround();
        if (!PlayerStateUtils.isInCobweb(ClientAccess.evernightMinecraft.player)) {
            if (!ClientAccess.evernightMinecraft.player.isPassenger()) {
                Setting<AntiCheatRotationsChoice> rotations = antiCheat.rotationsSetting;
                boolean motionSynchronized = false;
                if (rotations.getValue() == AntiCheatRotationsChoice.MOTION) {
                    Setting<Boolean> movementSync = antiCheat.movementSyncSetting;
                    motionSynchronized = movementSync.getValue();
                }
                if (motionSynchronized) {
                    return this.shouldWaitForMotionCritical(onGround);
                }
                return this.shouldWaitForAirborneCritical(onGround);
            }
        }
        return false;
    }

    private boolean shouldWaitForCritical(Entity target) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (flight.isToggled() || player.getAbilities().flying || player.isFallFlying()
                || player.onClimbable() || PlayerStateUtils.isInLiquid(player)) {
            return false;
        }
        if (criticals.isToggled()) {
            if (criticals.getMode() == CriticalsModule.ModeChoice.GRIM) {
                return false;
            }
        }
        Holder<MobEffect> levitation = MobEffects.LEVITATION;
        if (player.hasEffect(levitation)) {
            return false;
        }
        return target instanceof LivingEntity && this.shouldWaitForCriticalMovement();
    }

    private boolean shouldWaitForAirborneCritical(boolean onGround) {
        if (PlayerStateUtils.getTrackedFallDistance() <= 0.0F && !onGround) {
            return true;
        }
        if (onGround) {
            return ClientContext.playerStateTracker.getGroundedTicks() <= 1;
        }
        return false;
    }

    private boolean shouldWaitForMotionCritical(boolean onGround) {
        double currentY = ClientContext.playerStateTracker.getCurrentTickY();
        PlayerStateTracker previousYSource = ClientContext.playerStateTracker;
        float verticalMovement = (float) (currentY - previousYSource.getPreviousTickY());
        AABB collisionBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        if (verticalMovement > 0.0F) {
            double top = collisionBounds.maxY;
            AABB upperSlice = collisionBounds.setMinY(top);
            double upperY = verticalMovement;
            collisionBounds = upperSlice.deflate(0.0, upperY, 0.0);
        } else {
            double bottom = collisionBounds.minY;
            AABB lowerSlice = collisionBounds.setMaxY(bottom);
            double lowerY = verticalMovement;
            collisionBounds = lowerSlice.deflate(0.0, lowerY, 0.0);
        }
        CollisionGetter descendingLevel = ClientAccess.evernightMinecraft.level;
        LocalPlayer descendingPlayer = ClientAccess.evernightMinecraft.player;
        AABB descendingBounds = collisionBounds;
        if (!descendingLevel.noBlockCollision(descendingPlayer, descendingBounds) && verticalMovement < 0.0F) {
            return true;
        }
        CollisionGetter ascendingLevel = ClientAccess.evernightMinecraft.level;
        LocalPlayer ascendingPlayer = ClientAccess.evernightMinecraft.player;
        AABB ascendingBounds = collisionBounds;
        if (!ascendingLevel.noBlockCollision(ascendingPlayer, ascendingBounds) && verticalMovement > 0.0F) {
            return false;
        }
        return PlayerStateUtils.getTrackedFallDistance() <= 0.0F && !onGround;
    }

    public boolean canAttack(Entity target, float attackStrength) {
        if (target instanceof EndCrystal) {
            return true;
        }
        if (this.shouldWaitForCritical(target)) {
            return false;
        }
        if (!(target instanceof Player) && target instanceof LivingEntity livingTarget) {
            if (!antiCheat.is2b2tEnabled()) {
                double health = EntityUtils.getHealthWithAbsorption(livingTarget);
                if (health <= 1.0D) {
                    float maximumHealth = livingTarget.getMaxHealth();
                    if (maximumHealth <= 4.0F) {
                        double strength = attackStrength;
                        return strength >= 0.2D;
                    }
                }
            }
        }
        AuraModule delaySource = this.aura;
        return attackStrength >= delaySource.getAttackDelay();
    }
}