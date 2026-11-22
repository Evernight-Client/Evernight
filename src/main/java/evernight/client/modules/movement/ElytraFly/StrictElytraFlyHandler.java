package evernight.client.modules.movement.ElytraFly;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.player.TickRateController;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule.TakeoffChoice;
import evernight.client.settings.core.Setting;
import evernight.client.utils.movement.ElytraFlyModeHandler;
import evernight.client.utils.movement.MovementPhysics;
import evernight.mixin.minecraft.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.LocalPlayerTickEvent;

public class StrictElytraFlyHandler extends ElytraFlyModeHandler {
    private long accelerationStartTimeMillis;
    private boolean takeoffPending;
    private boolean awaitingFlight;
    private boolean fireworkLaunchPending;

    public StrictElytraFlyHandler(ElytraFlyModule module) {
        super(module);
    }

    @Override
    public void onEnable() {
        if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
            if (!ClientAccess.evernightMinecraft.player.onGround()) {
                this.takeoffPending = true;
                ClientAccess.evernightMinecraft.player.stopFallFlying();
            }
        }
        this.awaitingFlight = false;
        this.fireworkLaunchPending = false;
        this.accelerationStartTimeMillis = System.currentTimeMillis();
    }

    @Override
    public void handleOutgoingPacket(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
                if (!ClientAccess.evernightMinecraft.player.onGround()) {
                    Vec3 velocity = ClientAccess.evernightMinecraft.player.getDeltaMovement();
                    double verticalMotion = velocity.y();
                    if (verticalMotion < 0.1D && MovementPhysics.isLocalPlayerMoving()) {
                        ServerboundMovePlayerPacketAccessor packetAccess = (ServerboundMovePlayerPacketAccessor) packet;
                        packetAccess.setPitch(-5.0F);
                    }
                }
            }
        }
    }

    @Override
    public void handlePlayerTick(LocalPlayerTickEvent event) {
        if (ClientAccess.evernightMinecraft.player.onGround()) {
            this.takeoffPending = false;
        } else {
            if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
                this.takeoffPending = true;
            }
        }
        Input input = ClientAccess.evernightMinecraft.player.input.keyPresses;
        boolean requestTakeoff = input.jump();
        Setting<TakeoffChoice> takeoffSetting = this.module.takeoffSetting;
        if (takeoffSetting.getValue() == ElytraFlyModule.TakeoffChoice.STRICT) {
            LocalPlayer strictGroundPlayer = ClientAccess.evernightMinecraft.player;
            requestTakeoff = false;
            if (!strictGroundPlayer.onGround()) {
                Vec3 velocity = ClientAccess.evernightMinecraft.player.getDeltaMovement();
                double verticalMotion = velocity.y();
                requestTakeoff = verticalMotion < 0.1D;
            }
        } else {
            ElytraFlyModule timerOwner = this.module;
            TickRateController timerController = ClientContext.tickRateController;
            timerController.releaseMultiplier(timerOwner);
        }
        if (requestTakeoff || this.awaitingFlight) {
            this.module.updateTakeoff();
            LocalPlayer playerAfterTakeoff = ClientAccess.evernightMinecraft.player;
            this.awaitingFlight = !playerAfterTakeoff.isFallFlying();
        }
    }

    @Override
    public void handleMovement(LocalPlayerMoveEvent event) {
        if (!ClientAccess.evernightMinecraft.player.isFallFlying() && this.takeoffPending
                && !ClientAccess.evernightMinecraft.player.horizontalCollision) {
            Vec3 fallingVelocity = ClientAccess.evernightMinecraft.player.getDeltaMovement();
            if (fallingVelocity.y() < 0.0) {
                this.awaitingFlight = true;
            }
        }
        if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
            if (!MovementPhysics.isLocalPlayerMoving()) {
                this.accelerationStartTimeMillis = System.currentTimeMillis();
            }
            float maximumSpeed = this.module.strictSpeedSetting.getValue();
            float startSpeed = this.module.startSetting.getValue();
            float elapsedMillis = (float) (System.currentTimeMillis() - this.accelerationStartTimeMillis);
            float progress = Mth.clamp(elapsedMillis / 500.0F, 0.0F, 1.0F);
            float speed = Mth.lerp(progress, startSpeed, maximumSpeed);
            double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(event, speed);
            ClientAccess.evernightMinecraft.player.setDeltaMovement(horizontalMotion[0], 0.0D, horizontalMotion[1]);
            event.setY(0.0D);
            ClientAccess.evernightMinecraft.player.setDeltaMovement(
                    ClientAccess.evernightMinecraft.player.getDeltaMovement().with(Axis.Y, 0.0D));
        }
    }

    @Override
    public void handleIncomingPacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
                if (!ClientAccess.evernightMinecraft.player.onGround()) {
                    this.accelerationStartTimeMillis = System.currentTimeMillis();
                    ClientAccess.evernightMinecraft.player.stopFallFlying();
                }
            }
        }
    }

    @Override
    public void handleSendPosition(LocalPlayerSendPositionEvent event) {
        if (event.getStage() == EventStage.Pre) {
            boolean adjustRotation = false;
            if (this.module.isElytraEquipped()) {
                if (!event.isOnGround()) {
                    LocalPlayer player = ClientAccess.evernightMinecraft.player;
                    if (!player.isFallFlying()) {
                        adjustRotation = true;
                        this.fireworkLaunchPending = false;
                    } else if (!this.fireworkLaunchPending) {
                        if (!MovementPhysics.isLocalPlayerMoving()) {
                            adjustRotation = true;
                        } else {
                            this.fireworkLaunchPending = true;
                        }
                    }
                }
            }
            if (adjustRotation) {
                float originalYaw = event.getYaw();
                int yawTick = ClientAccess.evernightMinecraft.player.tickCount;
                int yawOffset = yawTick % 2 == 0 ? -2 : 2;
                float yaw = originalYaw + yawOffset;
                event.setYaw(yaw);
                float originalPitch = event.getPitch();
                int pitchTick = ClientAccess.evernightMinecraft.player.tickCount;
                int pitchOffset = pitchTick % 2 == 0 ? -2 : 2;
                float pitch = originalPitch + pitchOffset;
                event.setPitch(pitch);
            }
        }
    }
}