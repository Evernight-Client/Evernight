package evernight.client.modules.client.AntiCheat;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.PacketEvent;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AntiCheatRotationsChoice;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.movement.MovementSpeedTracker;
import evernight.client.utils.network.PacketSender;
import evernight.mixin.minecraft.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class AntiCheatMovementHandler implements ClientAccess {
    private final AntiCheatModule module;

    public AntiCheatMovementHandler(AntiCheatModule antiCheatModule) {
        this.module = antiCheatModule;
    }

    public boolean isBaritoneCompatibilityOverrideActive() {
        return false;
    }

    boolean shouldOffsetPacketYaw(ServerboundMovePlayerPacket packet) {
        if (!MovementPhysics.isLocalPlayerMoving()) {
            MovementSpeedTracker speedTracker = ClientContext.movementSpeedTracker;
            double averageSpeed = speedTracker.getAverageSpeed();
            if (averageSpeed < 5.0D) {
                return false;
            }
        }
        Setting<Boolean> twoBTwoTSetting = this.module.value2b2tSetting;
        Boolean twoBTwoTEnabled = twoBTwoTSetting.getValue();
        if (!twoBTwoTEnabled) {
            return false;
        }
        if (PlayerStateUtils.isFallFlying()) {
            return false;
        }
        if (packet != null) {
            boolean inventoryScreenWithPlayerMenu = ClientAccess.evernightMinecraft.screen instanceof AbstractContainerScreen
                    && ClientAccess.evernightMinecraft.player.containerMenu
                    == ClientAccess.evernightMinecraft.player.inventoryMenu;
            if (!packet.hasRotation() && !inventoryScreenWithPlayerMenu) {
                return false;
            }
        }
        return this.module.movementYawOffsetEnabled || packet == null;
    }

    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && this.shouldOffsetPacketYaw(packet)) {
            ((ServerboundMovePlayerPacketAccessor) packet).setYaw(packet.getYRot(0.0F) + AntiCheatModule.ROTATION_JITTER_STEP);
        }
    }

    public void prepareMovement() {
        if (this.shouldPrepareMovement()) {
            Setting<AntiCheatRotationsChoice> rotationModeSetting = this.module.rotationsSetting;
            if (rotationModeSetting.getValue() == AntiCheatRotationsChoice.SILENT) {
                LocalPlayer playerForX = ClientAccess.evernightMinecraft.player;
                double playerX = playerForX.getX();
                LocalPlayer playerForY = ClientAccess.evernightMinecraft.player;
                double playerY = playerForY.getY();
                LocalPlayer playerForZ = ClientAccess.evernightMinecraft.player;
                double playerZ = playerForZ.getZ();
                LocalPlayer playerForYaw = ClientAccess.evernightMinecraft.player;
                float playerYaw = playerForYaw.getYRot();
                LocalPlayer playerForPitch = ClientAccess.evernightMinecraft.player;
                float playerPitch = playerForPitch.getXRot();
                LocalPlayer playerForGround = ClientAccess.evernightMinecraft.player;
                boolean capturedOnGround = playerForGround.onGround();
                PacketSender.sendPositionRotation(
                        playerX, playerY, playerZ, playerYaw, playerPitch, capturedOnGround);
            }
        }
    }

    public boolean shouldPrepareMovement() {
        return this.shouldOffsetPacketYaw(null);
    }
}