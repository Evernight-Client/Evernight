package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.managers.player.FakeplayerManager;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.movement.Speed.SpeedModeHandler;
import evernight.client.modules.movement.Speed.SpeedModule;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GrimSpeedHandler extends SpeedModeHandler {
    private static final double GRIM_HARD_COLLIDE_REDUCE = 1.2D;
    private static final double GRIM_OFFSET_THRESHOLD = 0.001D;
    private static final int GRIM_HARD_COLLIDE_GRACE_TICKS = 3;
    private static AntiCheatModule antiCheatModule;

    static {
        antiCheatModule = ClientContext.getModule(AntiCheatModule.class);
    }

    private AABB lastPlayerBounds;
    private int lastHardCollideTick = -100;

    public GrimSpeedHandler(SpeedModule speedModule) {
        super(speedModule);
    }

    public static void bindModules(ModuleRegistry modules) {
        antiCheatModule = modules.getModule(AntiCheatModule.class);
    }

    private boolean intersectsNearbyEntity(Entity entity) {
        boolean strictBounds = antiCheatModule.is2b2tEnabled();
        AABB entityBounds = entity.getBoundingBox();
        AABB playerBounds = this.lastPlayerBounds;
        AABB expandedPlayerBounds = strictBounds
                ? playerBounds.inflate(0.4F, 0.2F, 0.4F)
                : playerBounds.inflate(1.0D);
        return entityBounds.intersects(expandedPlayerBounds);
    }

    @Override
    public void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (EventStage.Post == event.getStage()) {
            this.lastPlayerBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        }
    }

    @Override
    public void onMove(LocalPlayerMoveEvent event) {
        if (this.lastPlayerBounds == null || !MovementPhysics.isLocalPlayerMoving()
                || !ClientContext.serverStateManager.hasElapsedSinceSetback(100L)) {
            return;
        }
        if (this.speedModule.grimOffsetSetting.getValue()) {
            this.applyGrimOffsetBoost(event);
            return;
        }
        int collisionWeight = 0;
        for (BlockEntity blockEntity : WorldUtils.getLoadedBlockEntities()) {
            if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox) {
                AABB expandedPlayerBounds = this.lastPlayerBounds.inflate(0.5D);
                AABB shulkerBounds = shulkerBox.getBoundingBox(Blocks.SHULKER_BOX.defaultBlockState())
                        .move(blockEntity.getBlockPos());
                if (expandedPlayerBounds.intersects(shulkerBounds)
                        && shulkerBox.getAnimationStatus() != AnimationStatus.CLOSED
                        && !antiCheatModule.is2b2tEnabled()) {
                    collisionWeight += 4;
                }
            }
        }
        float collisionBoost = antiCheatModule.is2b2tEnabled() ? 0.06F : 0.08F;
        for (Entity entity : ClientAccess.evernightMinecraft.level.entitiesForRendering()) {
            if (!(entity instanceof FakeplayerManager.SimulatedPlayer)
                    && entity != ClientAccess.evernightMinecraft.player
                    && entity.isPushable() && this.intersectsNearbyEntity(entity)) {
                if (entity instanceof Boat) {
                    collisionWeight += 4;
                }
                collisionWeight++;
            }
        }
        if (collisionWeight != 0) {
            Vec3 horizontalDirection = event.getVelocity().normalize().with(Axis.Y, 0.0D);
            Vec3 velocity = event.getVelocity();
            float boost = collisionBoost * collisionWeight;
            event.setVelocity(velocity.add(horizontalDirection.scale(boost)));
        }
    }

    /**
     * Grim only removes its movement offset (up to 1.2 blocks per tick) for a few ticks after a hard
     * collision with a lerping entity - boats, shulkers, striders and happy ghasts - or, while riding
     * a boat, with any pushable entity. Boosting outside that window is pure Simulation offset.
     */
    private void applyGrimOffsetBoost(LocalPlayerMoveEvent event) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        int collisionWeight = this.countLerpingCollisions(player);
        if (collisionWeight > 0) {
            this.lastHardCollideTick = player.tickCount;
        }
        if (collisionWeight == 0 && player.tickCount - this.lastHardCollideTick > GRIM_HARD_COLLIDE_GRACE_TICKS) {
            return;
        }
        float collisionBoost = antiCheatModule.is2b2tEnabled() ? 0.06F : 0.08F;
        double boost = Math.min(collisionBoost * Math.max(collisionWeight, 1),
                GRIM_HARD_COLLIDE_REDUCE - GRIM_OFFSET_THRESHOLD);
        Vec3 horizontalDirection = event.getVelocity().normalize().with(Axis.Y, 0.0D);
        event.setVelocity(event.getVelocity().add(horizontalDirection.scale(boost)));
    }

    private int countLerpingCollisions(LocalPlayer player) {
        boolean strictBounds = antiCheatModule.is2b2tEnabled();
        AABB expandedPlayerBounds = strictBounds
                ? this.lastPlayerBounds.inflate(0.4D, 0.2D, 0.4D)
                : this.lastPlayerBounds.inflate(1.0D);
        boolean ridingBoat = player.getVehicle() instanceof Boat
                || player.getVehicle() instanceof AbstractChestBoat;
        int collisionWeight = 0;
        for (Entity entity : ClientAccess.evernightMinecraft.level.entitiesForRendering()) {
            if (entity == player || entity instanceof FakeplayerManager.SimulatedPlayer || !entity.isPushable()) {
                continue;
            }
            if (!entity.getBoundingBox().intersects(expandedPlayerBounds)) {
                continue;
            }
            boolean lerpingEntity = entity instanceof Boat || entity instanceof AbstractChestBoat
                    || entity.getType() == EntityType.SHULKER
                    || entity.getType() == EntityType.STRIDER
                    || entity.getType() == EntityType.HAPPY_GHAST;
            if (lerpingEntity || ridingBoat) {
                collisionWeight++;
            }
        }
        return collisionWeight;
    }
}