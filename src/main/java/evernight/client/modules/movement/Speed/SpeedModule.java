package evernight.client.modules.movement.Speed;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.DamageUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.movement.GrimSpeedHandler;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.movement.StrafeSpeedHandler;
import evernight.client.utils.movement.StrictStrafeSpeedHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.ClientPacketListenerHandleExplosionEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LivingEntityJumpFromGroundEvent;
import evernight.client.events.player.LivingEntityRotationEvent;
import evernight.client.events.player.LocalPlayerAiStepEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerMoveTowardsClosestSpaceEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.StepHeightEvent;
import evernight.client.modules.movement.ObstaclePasser.ObstaclePasserModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.modules.movement.EnumSelectionMap;

public class SpeedModule extends Module {
    private static ObstaclePasserModule obstaclePasserModule;
    public final Setting<ModeChoice> modeSetting;
    public final Setting<Float> speedSetting;
    public final Setting<Boolean> boostSetting;
    public final Setting<Boolean> inLiquidSetting;
    public final Setting<Boolean> useTimerSetting;
    public final Setting<Boolean> pauseInBlocksSetting;
    public final Setting<Boolean> grimOffsetSetting;
    public final ElapsedTimer explosionBoostTimer;
    private final EnumSelectionMap<SpeedModeHandler> modeHandlers;
    private boolean skipNextMoveAfterStep;

    public SpeedModule() {
        super("Speed", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.Grim));
        Predicate<Object> speedVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.StrafeStrict
                && this.modeSetting.getValue() != ModeChoice.Grim;
        this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.0F, 10.0F, speedVisibility));
        Predicate<Object> boostVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.StrafeStrict
                || this.modeSetting.getValue() == ModeChoice.Strafe;
        this.boostSetting = this.add(new BooleanSetting("Boost", false, boostVisibility));
        Predicate<Object> liquidVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.Grim;
        this.inLiquidSetting = this.add(new BooleanSetting("InLiquid", false, liquidVisibility));
        Predicate<Object> timerVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.Grim;
        this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true, timerVisibility));
        Predicate<Object> blocksVisibility = ignored -> this.modeSetting.getValue() != ModeChoice.Grim;
        this.pauseInBlocksSetting = this.add(new BooleanSetting("PauseInBlocks", true, blocksVisibility));
        Predicate<Object> grimOffsetVisibility = ignored -> this.modeSetting.getValue() == ModeChoice.Grim;
        this.grimOffsetSetting = this.add(new BooleanSetting("GrimOffset", true, grimOffsetVisibility));
        this.explosionBoostTimer = new ElapsedTimer();
        this.modeHandlers = new EnumSelectionMap<>(this.modeSetting);
        this.modeHandlers.register(ModeChoice.Strafe, new StrafeSpeedHandler(this));
        this.modeHandlers.register(ModeChoice.StrafeStrict, new StrictStrafeSpeedHandler(this));
        this.modeHandlers.register(ModeChoice.Vanilla, new VanillaSpeedHandler(this));
        this.modeHandlers.register(ModeChoice.OnGround, new OnGroundSpeedHandler(this));
        this.modeHandlers.register(ModeChoice.Grim, new GrimSpeedHandler(this));
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        obstaclePasserModule = registry.getModule(ObstaclePasserModule.class);
        StrafeSpeedHandler.bindModules(registry);
        StrictStrafeSpeedHandler.bindModules(registry);
        GrimSpeedHandler.bindModules(registry);
    }

    public void reset() {
        SpeedModeHandler handler = this.modeHandlers.getSelected();
        handler.lastHorizontalDistance = 0.0;
        handler.moveSpeed = 0.0;
        handler.movementStage = this.modeSetting.getValue() == ModeChoice.OnGround ? 2 : 4;
    }

    @Override
    public String getInfo() {
        ModeChoice selectedMode = this.modeSetting.getValue();
        return selectedMode.getName();
    }

    @Override
    public void onEnable() {
        this.modeHandlers.getSelected().onEnable();
    }

    @EventHandler(priority = 100)
    private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
        if (ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(10L)) {
            this.modeHandlers.getSelected().onSendPosition(event);
        }
    }

    @EventHandler
    private void onLocalPlayerAiStepEvent(LocalPlayerAiStepEvent event) {
        if (this.modeSetting.getValue() == ModeChoice.StrafeStrict && !this.shouldPauseSpeed()) {
            ClientAccess.evernightMinecraft.player.setSprinting(true);
        }
    }

    @EventHandler
    private void onStepHeight(StepHeightEvent event) {
        if (EventStage.Post == event.getStage() && ClientAccess.evernightMinecraft.player.onGround() && event.getHeight() > 0.6D) {
            this.skipNextMoveAfterStep = true;
        }
    }

    @EventHandler
    private void onLocalPlayerMoveTowardsClosestSpaceEvent(LocalPlayerMoveTowardsClosestSpaceEvent event) {
        if (this.modeSetting.getValue() == ModeChoice.Grim) {
            event.cancel();
        }
    }

    @EventHandler
    private void onClientPacketListenerHandleExplosionEvent(ClientPacketListenerHandleExplosionEvent event) {
        Vec3 explosionPosition = new Vec3(event.getCenterX(), event.getCenterY(), event.getCenterZ());
        double explosionDamage = DamageUtils.getCrystalDamage(explosionPosition, ClientAccess.evernightMinecraft.player);
        if (!MovementPhysics.isLocalPlayerMoving()) {
            return;
        }
        if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
            return;
        }
        if (!(this.boostSetting.getValue())) {
            return;
        }
        double explosionDistance = Math.sqrt(ClientAccess.evernightMinecraft.player.distanceToSqr(explosionPosition));
        if (explosionDistance > 6.0D || explosionDamage < 4.0D) {
            return;
        }
        this.explosionBoostTimer.reset();
    }

    @EventHandler
    private void onLivingEntityJumpFromGroundEvent(LivingEntityJumpFromGroundEvent event) {
        if (this.modeSetting.getValue() == ModeChoice.StrafeStrict
                && MovementPhysics.isLocalPlayerMoving() && !this.shouldPauseSpeed()) {
            event.cancel();
        }
    }

    @EventHandler
    private void onLocalPlayerMoveEvent(LocalPlayerMoveEvent event) {
        if (!ClientContext.serverStateManager.getTeleportAckTimer().hasElapsed(10L)) {
            return;
        }
        if (this.skipNextMoveAfterStep) {
            this.skipNextMoveAfterStep = false;
        } else {
            this.modeHandlers.getSelected().onMove(event);
        }
    }

    @EventHandler
    private void onRotationEvent(LivingEntityRotationEvent event) {
        if (!event.isJump()) {
            this.modeHandlers.getSelected().onRotationUpdate();
        }
    }

    public boolean shouldYieldToObstaclePasser() {
        return obstaclePasserModule != null && obstaclePasserModule.isPassingObstacles();
    }

    public boolean shouldPauseInBlocks() {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        AABB playerBounds = player.getBoundingBox();
        BlockPos position = player.blockPosition();
        AABB column = new AABB(position.getX(), playerBounds.minY, position.getZ(),
                position.getX() + 1.0D, playerBounds.maxY, position.getZ() + 1.0D).deflate(1e-7D);
        return ClientAccess.evernightMinecraft.level.collidesWithSuffocatingBlock(player, column)
                && this.pauseInBlocksSetting.getValue();
    }

    public boolean shouldPauseInLiquid() {
        return PlayerStateUtils.isInLiquid(ClientAccess.evernightMinecraft.player) && !this.inLiquidSetting.getValue();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.reset();
        }
    }

    public boolean shouldPauseSpeed() {
        return this.isWorldUnavailable() || this.shouldPauseInBlocks() || this.shouldPauseInLiquid()
                || PlayerStateUtils.isHungerLimited() || ClientAccess.evernightMinecraft.player.isFallFlying()
                || ClientAccess.evernightMinecraft.player.isSpectator() || ClientContext.baritoneBridge.isPathingOrCalculating();
    }

    public enum ModeChoice implements NamedEntry {
        Strafe, StrafeStrict, Vanilla, OnGround, Grim;

        @Override
        public String getName() {
            return this.name();
        }
    }
}