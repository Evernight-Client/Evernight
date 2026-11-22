package evernight.client.modules.movement.NoFall;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.combat.BlockRaycastContext;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import evernight.mixin.minecraft.network.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.LocalPlayerTickEvent;

public class NoFallModule extends Module {
    final Setting<ModeChoice> modeSetting;
    private final Setting<Float> groundDistanceSetting;
    private double previousFallDistance;
    private boolean predictedTopFaceCollision;

    public NoFallModule() {
        super("NoFall", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PACKET));
        Predicate<Object> groundDistanceVisible = ignored -> this.modeSetting.getValue() == ModeChoice.MLG;
        this.groundDistanceSetting = this.add(new NumberSetting<>("GroundDistance", 2.0F, 1.0F, 4.0F, groundDistanceVisible).withUnit("m"));
        for (NoFallModule.ModeChoice choice : NoFallModule.ModeChoice.values()) {
            choice.module = this;
        }
    }

    @Override
    public String getInfo() {
        return (this.modeSetting.getValue()).getName();
    }

    @EventHandler
    private void onConnectionSendPacketEvent(PacketEvent.Send event) {
        NoFallModule.ModeChoice mode = this.modeSetting.getValue();
        mode.handleOutgoingPacket(event);
    }

    @EventHandler
    private void onLocalPlayerPreTickEvent(LocalPlayerPreTickEvent event) {
        NoFallModule.ModeChoice mode = this.modeSetting.getValue();
        mode.beforePlayerTick();
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        NoFallModule.ModeChoice mode = this.modeSetting.getValue();
        mode.afterPlayerTick();
    }

    private HashSet<BlockPos> collectBottomCorners(AABB box) {
        return new HashSet<>(List.of(BlockPos.containing(box.maxX, box.minY, box.maxZ),
                BlockPos.containing(box.maxX, box.minY, box.minZ), BlockPos.containing(box.minX, box.minY, box.maxZ),
                BlockPos.containing(box.minX, box.minY, box.minZ)));
    }

    @EventHandler
    private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
        if (event.getStage() == EventStage.Pre) {
            this.updatePredictedTopFaceCollision();
        }
        NoFallModule.ModeChoice mode = this.modeSetting.getValue();
        mode.handleSendPosition(event);
        this.previousFallDistance = ClientContext.playerStateTracker.getFallDistance();
    }

    private void updatePredictedTopFaceCollision() {
        Vec3 currentPosition = ClientAccess.evernightMinecraft.player.position();
        double previousX = ClientAccess.evernightMinecraft.player.xo;
        double previousY = ClientAccess.evernightMinecraft.player.yo;
        double previousZ = ClientAccess.evernightMinecraft.player.zo;
        Vec3 prediction = currentPosition.subtract(previousX, previousY, previousZ).scale(1.1F);
        BlockRaycastContext.Builder builder = new BlockRaycastContext.Builder(
                ClientAccess.evernightMinecraft.player.position(), ClientAccess.evernightMinecraft.player.position().add(prediction)
        );
        BlockRaycastContext context = builder.build();
        BlockHitResult hit = RaycastUtils.raycast(context);
        if (hit.getType() == Type.MISS) {
            this.predictedTopFaceCollision = false;
        } else {
            this.predictedTopFaceCollision = hit.getDirection() == Direction.UP;
        }
    }

    public enum ModeChoice implements NamedEntry {
        PACKET("Packet") {
            @Override
            public void handleOutgoingPacket(PacketEvent.Send event) {
                if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket
                        && PlayerStateUtils.getTrackedFallDistance() > 2.1 && !PlayerStateUtils.isFallFlying()) {
                    ((ServerboundMovePlayerPacketAccessor) movementPacket).setOnGround(true);
                }
            }

            @Override
            public void handleSendPosition(LocalPlayerSendPositionEvent event) {
                if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
                    NoFallModule.ModeChoice.GRIM.handleSendPosition(event);
                }
            }
        },
        ANTI("Anti") {
            @Override
            public void handleOutgoingPacket(PacketEvent.Send event) {
                if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)
                        || !(PlayerStateUtils.getTrackedFallDistance() > 3.0F) || !this.canApplyFallProtection()) {
                    return;
                }
                ClientLevel world = ClientAccess.evernightMinecraft.level;
                AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
                double verticalExpansion = ClientAccess.evernightMinecraft.player.getDeltaMovement().y * 1.1;
                AABB expandedBounds = bounds.expandTowards(0.0, verticalExpansion, 0.0);
                if (world.noCollision(expandedBounds)) {
                    return;
                }
                LocalPlayer target = ClientAccess.evernightMinecraft.player;
                double x = ClientAccess.evernightMinecraft.player.getX();
                double y = ClientAccess.evernightMinecraft.player.getY() + 2.0;
                double z = ClientAccess.evernightMinecraft.player.getZ();
                target.setPosRaw(x, y, z);
            }
        },
        TELEPORT("Teleport") {
            private boolean teleportPending;

            @Override
            public void afterPlayerTick() {
                if (PlayerStateUtils.getTrackedFallDistance() > 3.0F) {
                    this.teleportPending = true;
                }
            }

            @Override
            public void handleSendPosition(LocalPlayerSendPositionEvent event) {
                if (!this.teleportPending) {
                    return;
                }
                if (!event.isOnGround()) {
                    return;
                }
                if (event.getStage() != EventStage.Pre) {
                    return;
                }
                if (!this.canApplyFallProtection()) {
                    return;
                }
                event.setX(0.0);
                event.setY(0.0);
                event.setZ(0.0);
                event.setOnGround(false);
                if (WorldUtils.getDimension().isEnd()) {
                    event.setY(64.0);
                }
                this.teleportPending = false;
            }
        },
        MLG("MLG") {
            private boolean skipNextMlgTick;

            @Override
            public void afterPlayerTick() {
                if (this.skipNextMlgTick) {
                    this.skipNextMlgTick = false;
                    return;
                }
                InventoryUtils.SwapSlot bucket = InventoryUtils.findSwapSlot(Items.WATER_BUCKET, true);
                int selectedSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
                if (PlayerStateUtils.getTrackedFallDistance() <= 3.0F) {
                    return;
                }
                if (!bucket.isFound()) {
                    return;
                }
                if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
                    return;
                }
                NoFallModule owner = this.module;
                AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
                HashSet<BlockPos> positions = owner.collectBottomCorners(bounds);
                Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
                positions.add(BlockPos.containing(playerPosition));
                for (int depth = 0; (float) depth <= this.module.groundDistanceSetting.getValue(); depth++) {
                    for (BlockPos position : positions) {
                        ClientLevel blockWorld = ClientAccess.evernightMinecraft.level;
                        BlockPos below = position.relative(Direction.DOWN, depth);
                        BlockState state = blockWorld.getBlockState(below);
                        if (!(state.getBlock() instanceof AirBlock)) {
                            ClientLevel fluidWorld = ClientAccess.evernightMinecraft.level;
                            if (fluidWorld.getFluidState(position).is(FluidTags.WATER)) {
                                return;
                            }
                            LocalPlayer pitchPlayer = ClientAccess.evernightMinecraft.player;
                            float pitch = MathConstants.NINETY_DEGREES;
                            pitchPlayer.setXRot(pitch);
                            InventoryUtils.swapTo(bucket);
                            MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
                            LocalPlayer usePlayer = ClientAccess.evernightMinecraft.player;
                            InteractionHand hand = bucket.hand();
                            gameMode.useItem(usePlayer, hand);
                            LocalPlayer swingPlayer = ClientAccess.evernightMinecraft.player;
                            InteractionHand swingHand = bucket.hand();
                            swingPlayer.swing(swingHand);
                            InventoryUtils.swapBack(selectedSlot, bucket);
                            this.skipNextMlgTick = true;
                            return;
                        }
                    }
                }
            }
        },
        GRIM("Grim") {
            @Override
            public void handleSendPosition(LocalPlayerSendPositionEvent event) {
                if (event.getStage() == EventStage.Post) {
                    return;
                }
                if (!ClientAccess.evernightMinecraft.player.onGround() || this.module.previousFallDistance < 3.0) {
                    return;
                }
                Vec3 position = ClientContext.playerStateTracker.getPacketPosition();
                double x = position.x();
                double y = position.y() + 1.0E-9;
                double z = position.z();
                float yaw = ClientAccess.evernightMinecraft.player.getYRot();
                float pitch = ClientAccess.evernightMinecraft.player.getXRot();
                PacketSender.sendPositionRotation(x, y, z, yaw, pitch, false);
            }
        },
        GRIM_JUMP("GrimJump") {
            private boolean jumpPending;

            @Override
            public void handleOutgoingPacket(PacketEvent.Send event) {
                if (event.getPacket() instanceof ServerboundMovePlayerPacketAccessor movementPacket
                        && (PlayerStateUtils.getTrackedFallDistance() > 3.0F || this.jumpPending)) {
                    if (!ClientAccess.evernightMinecraft.player.isFallFlying()) {
                        movementPacket.setOnGround(false);
                        this.jumpPending = true;
                    }
                }
            }

            @Override
            public void beforePlayerTick() {
                if (!this.jumpPending) {
                    return;
                }
                if (!ClientAccess.evernightMinecraft.player.onGround()) {
                    return;
                }
                ClientContext.inputOverrides.requestJump(true);
                this.jumpPending = false;
            }
        };
        private final String displayName;
        protected NoFallModule module;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        public void afterPlayerTick() {
        }

        @Override
        public String getName() {
            return this.displayName;
        }

        public void handleOutgoingPacket(PacketEvent.Send event) {
        }

        public void handleSendPosition(LocalPlayerSendPositionEvent event) {
        }

        public void beforePlayerTick() {
        }

        protected boolean canApplyFallProtection() {
            if (!ClientAccess.evernightMinecraft.player.isFallFlying()) {
                return true;
            }
            float pitch = ClientContext.playerStateTracker.getPacketRotations()[1];
            return Math.abs(pitch) <= 45.0F || this.module.predictedTopFaceCollision;
        }
    }
}