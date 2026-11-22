package evernight.client.utils.movement;

import evernight.client.ClientAccess;
import evernight.client.access.LocalPlayerTickAccess;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.modules.movement.FastWeb.FastWebModule;
import evernight.client.modules.movement.Sprint.SprintModule;
import evernight.client.modules.movement.Velocity.VelocityModule;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.MathConstants;
import evernight.mixin.minecraft.entity.LocalPlayerAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.ClientPacketListenerHandleExplosionEvent;
import evernight.client.events.network.ClientPacketListenerHandleSetEntityMotionEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;

public class WallsVelocityHandler extends VelocityModeHandler {
    private static SprintModule sprintModule;
    private static FastWebModule fastWebModule;
    private final List<Long> recentPositionCorrections = Collections.synchronizedList(new ArrayList<>());
    private final ElapsedTimer correctionCooldown = new ElapsedTimer();
    private boolean groundCollisionPresent;

    public WallsVelocityHandler(VelocityModule velocityModule) {
        super(velocityModule);
    }

    public static boolean hasRaisedPlayerCollision() {
        return hasPlayerCollisionAtHeight(ClientAccess.evernightMinecraft.player.getY() + 0.1D);
    }

    private static boolean allOccupiedBlocksHaveCollision() {
        Set<BlockPos> positions = PlayerStateUtils.getSurroundPositions(ClientAccess.evernightMinecraft.player);
        if (positions.size() != 4) {
            return false;
        }
        for (BlockPos position : positions) {
            if (ClientAccess.evernightMinecraft.level.getBlockState(position)
                    .getCollisionShape(ClientAccess.evernightMinecraft.level, position).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasUsableGroundCollision() {
        return !PlayerStateUtils.isFallFlying() && hasRaisedPlayerCollision();
    }

    public static boolean hasCollisionAtHeight(Player player, double height) {
        AABB bounds = player.getBoundingBox();
        bounds = bounds.deflate(BoundingBoxUtils.blockCornerInset, 0.0, BoundingBoxUtils.blockCornerInset);
        bounds = bounds.setMinY(height);
        AABB collisionBounds = bounds.setMaxY(height + 0.1D);
        return BlockPos.betweenClosedStream(collisionBounds).anyMatch(position -> intersectsCollision(collisionBounds, position));
    }

    private static boolean intersectsCollision(AABB bounds, BlockPos position) {
        BlockStateBase state = ClientAccess.evernightMinecraft.level.getBlockState(position);
        VoxelShape shape = state.getCollisionShape(ClientAccess.evernightMinecraft.level, position);
        return !shape.isEmpty() && bounds.intersects(shape.bounds().move(position));
    }

    public static boolean hasPlayerCollisionAtHeight(double height) {
        return hasCollisionAtHeight(ClientAccess.evernightMinecraft.player, height);
    }

    public static void bindModules(ModuleRegistry modules) {
        sprintModule = modules.getModule(SprintModule.class);
        fastWebModule = modules.getModule(FastWebModule.class);
    }

    private float getAbsolutePositionDeltaSum() {
        double deltaSum = ClientAccess.evernightMinecraft.player.getX() - ClientAccess.evernightMinecraft.player.xo;
        deltaSum = deltaSum + ClientAccess.evernightMinecraft.player.getY() - ClientAccess.evernightMinecraft.player.yo;
        deltaSum = deltaSum + ClientAccess.evernightMinecraft.player.getZ() - ClientAccess.evernightMinecraft.player.zo;
        return (float) Math.abs(deltaSum);
    }

    private boolean canApplyWallVelocity() {
        return this.isStationaryStaticMode() || this.groundCollisionPresent;
    }

    private boolean isStationaryStaticMode() {
        return this.velocityModule.staticSetting.getValue() && !PlayerStateUtils.isFallFlying()
                && this.getAbsolutePositionDeltaSum() < 0.01D;
    }

    private void applyYawAndPitch(LocalPlayerSendPositionEvent event) {
        LocalPlayerAccessor access = (LocalPlayerAccessor) ClientAccess.evernightMinecraft.player;
        float previousYaw = access.lastYaw();
        boolean moving = MovementPhysics.isLocalPlayerMoving();
        boolean yawWithinThreshold = Mth.degreesDifferenceAbs(event.getYaw(), previousYaw) <= 2.0F;
        if (sprintModule.isToggled() && sprintModule.shouldDeferToVanillaSprinting()
                && !allOccupiedBlocksHaveCollision() && yawWithinThreshold) {
            moving = false;
        }
        if (!moving) {
            if (this.isStationaryStaticMode()) {
                access.evernight$setTicksSinceLastPositionPacketSent(Math.min(access.evernight$getTicksSinceLastPositionPacketSent(), 18));
            }
            event.setYaw(previousYaw);
        }
        event.setPitch(89.0F);
    }

    @Override
    public void onEntityMotion(ClientPacketListenerHandleSetEntityMotionEvent event) {
        int entityId = event.getEntityId();
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (entityId == player.getId() && this.shouldCancelVelocity()) {
            Vec3 incomingVelocity = event.getVelocity();
            double verticalSpeed = Math.abs(incomingVelocity.y);
            if (verticalSpeed < 0.0788D && incomingVelocity.x == 0.0 && incomingVelocity.z == 0.0) {
                return;
            }
            event.setVelocity(0.0F, 0.0F, 0.0F);
            event.cancel();
        }
    }

    @Override
    public void onExplosion(ClientPacketListenerHandleExplosionEvent event) {
        if (this.shouldCancelVelocity()) {
            event.setVelocityX(0.0F);
            event.setVelocityY(0.0F);
            event.setVelocityZ(0.0F);
        }
    }

    private void applyRandomizedPitch(LocalPlayerSendPositionEvent event) {
        LocalPlayerAccessor access = (LocalPlayerAccessor) ClientAccess.evernightMinecraft.player;
        access.evernight$setTicksSinceLastPositionPacketSent(20);
        LocalPlayerTickAccess tickAccess = (LocalPlayerTickAccess) ClientAccess.evernightMinecraft.player;
        tickAccess.evernight$updateServer((float) Math.random(), (float) Math.random());
        float jitter = AntiCheatModule.ROTATION_JITTER_STEP * (ClientAccess.evernightMinecraft.player.tickCount % 6 - 3);
        event.setPitch(Mth.clamp(event.getPitch() + jitter, -MathConstants.NINETY_DEGREES, MathConstants.NINETY_DEGREES));
    }

    public boolean shouldCancelVelocity() {
        if (fastWebModule.isToggled() && fastWebModule.isGrimEnabled()
                && PlayerStateUtils.isInCobweb(ClientAccess.evernightMinecraft.player)) {
            return true;
        }
        return this.correctionCooldown.hasElapsed(150L) && this.canApplyWallVelocity();
    }

    @Override
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.recentPositionCorrections.add(System.currentTimeMillis());
        }
    }

    @Override
    public void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (EventStage.Post == event.getStage()
                || ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen
                || !this.canApplyWallVelocity() || !this.velocityModule.reduceSetting.getValue()) {
            return;
        }
        if (RotationManager.isSilentRotationMode()) {
            this.applyRandomizedPitch(event);
        } else {
            this.applyYawAndPitch(event);
        }
    }

    @Override
    public void onTick() {
        this.groundCollisionPresent = hasUsableGroundCollision();
        this.recentPositionCorrections.removeIf(receivedAt -> System.currentTimeMillis() > receivedAt + 500L);
        if (this.recentPositionCorrections.size() > 2) {
            this.correctionCooldown.reset();
        }
    }
}