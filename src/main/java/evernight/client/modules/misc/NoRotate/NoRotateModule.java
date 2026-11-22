package evernight.client.modules.misc.NoRotate;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.mixin.minecraft.entity.PositionMoveRotationAccessor;
import evernight.mixin.minecraft.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Set;

public class NoRotateModule extends Module {
    private final Setting<InBlocksChoice> inBlocksSetting;
    private float spoofYaw;
    private float spoofPitch;
    private boolean spoofPending;

    public NoRotateModule() {
        super("NoRotate", ModuleCategory.MISC);
        NoRotateModule.InBlocksChoice inBlocksDefault = NoRotateModule.InBlocksChoice.OFF;
        this.inBlocksSetting = this.add(new EnumSetting<>("InBlocks", inBlocksDefault));
    }

    private boolean isInsideBlockingShape() {
        LocalPlayer ladderPlayer = ClientAccess.evernightMinecraft.player;
        if (ladderPlayer.isSuppressingSlidingDownLadder()) {
            return false;
        }
        LocalPlayer cobwebPlayer = ClientAccess.evernightMinecraft.player;
        if (PlayerStateUtils.isInCobweb(cobwebPlayer)) {
            return false;
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        LocalPlayer blockPlayer = ClientAccess.evernightMinecraft.player;
        BlockPos blockPosition = blockPlayer.blockPosition();
        BlockState blockState = level.getBlockState(blockPosition);
        if (blockState.getBlock() instanceof FallingBlock) {
            return false;
        }
        ClientLevel collisionLevel = ClientAccess.evernightMinecraft.level;
        BlockPos collisionPosition = BlockPos.ZERO;
        VoxelShape collisionShape = blockState.getCollisionShape(collisionLevel, collisionPosition);
        if (collisionShape.isEmpty()) {
            return false;
        }
        LocalPlayer heightPlayer = ClientAccess.evernightMinecraft.player;
        double playerY = heightPlayer.getY();
        LocalPlayer floorPlayer = ClientAccess.evernightMinecraft.player;
        double floorY = floorPlayer.getY();
        double heightWithinBlock = playerY - Math.floor(floorY);
        if (heightWithinBlock >= collisionShape.bounds().maxY) {
            return false;
        }
        AABB localShapeBounds = collisionShape.bounds();
        LocalPlayer positionPlayer = ClientAccess.evernightMinecraft.player;
        BlockPos shapePosition = positionPlayer.blockPosition();
        AABB worldShapeBounds = localShapeBounds.move(shapePosition);
        LocalPlayer boundsPlayer = ClientAccess.evernightMinecraft.player;
        AABB playerBounds = boundsPlayer.getBoundingBox();
        AABB inflatedPlayerBounds = playerBounds.inflate(-0.05D);
        return worldShapeBounds.intersects(inflatedPlayerBounds);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket && this.spoofPending) {
            ServerboundMovePlayerPacketAccessor packetAccessor = (ServerboundMovePlayerPacketAccessor) movementPacket;
            packetAccessor.setYaw(this.spoofYaw);
            packetAccessor.setPitch(this.spoofPitch);
            this.spoofPending = false;
        }
        Setting<InBlocksChoice> inBlocksSettingSnapshot = this.inBlocksSetting;
        if (inBlocksSettingSnapshot.getValue() == NoRotateModule.InBlocksChoice.CANCEL
                && !MovementPhysics.isLocalPlayerMoving() && event.getPacket() instanceof ServerboundMovePlayerPacket
                && this.isInsideBlockingShape()) {
            event.cancel();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket positionPacket)) {
            return;
        }
        if (this.isWorldUnavailable()) {
            return;
        }
        int playerTicks = ClientAccess.evernightMinecraft.player.tickCount;
        if (playerTicks <= (20)) {
            return;
        }
        Minecraft minecraft = ClientAccess.evernightMinecraft;
        if (minecraft.isLocalServer()) {
            return;
        }
        LocalPlayer positionPlayer = ClientAccess.evernightMinecraft.player;
        PositionMoveRotation playerPosition = PositionMoveRotation.of(positionPlayer);
        PositionMoveRotation serverChange = positionPacket.change();
        Set<Relative> relativeFlags = positionPacket.relatives();
        PositionMoveRotation absolutePosition = PositionMoveRotation.calculateAbsolute(playerPosition, serverChange, relativeFlags);
        LocalPlayer yawPlayer = ClientAccess.evernightMinecraft.player;
        float preservedYaw = yawPlayer.getYRot();
        LocalPlayer pitchPlayer = ClientAccess.evernightMinecraft.player;
        float preservedPitch = pitchPlayer.getXRot();
        if (positionPacket.relatives().contains(Relative.Y_ROT)) {
            preservedYaw = 0.0F;
        }
        if (positionPacket.relatives().contains(Relative.X_ROT)) {
            preservedPitch = 0.0F;
        }
        ((PositionMoveRotationAccessor) (Object) positionPacket.change()).evernight$setYaw(preservedYaw);
        ((PositionMoveRotationAccessor) (Object) positionPacket.change()).evernight$setPitch(preservedPitch);
        Setting<InBlocksChoice> inBlocksSettingSnapshot = this.inBlocksSetting;
        if (inBlocksSettingSnapshot.getValue() == NoRotateModule.InBlocksChoice.SPOOF
                || RotationManager.isMovementSyncEnabled()) {
            this.spoofYaw = absolutePosition.yRot();
            this.spoofPitch = absolutePosition.xRot();
            this.spoofPending = true;
        }
    }

    enum InBlocksChoice implements NamedEntry {
        OFF("Off"),
        CANCEL("Cancel"),
        SPOOF("Spoof");
        private final String displayName;

        InBlocksChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}