package evernight.client.modules.combat.SelfFill;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.text.Translations;
import evernight.client.settings.options.ListFilterMode;
import evernight.client.utils.core.LockUtils;
import evernight.client.utils.chat.ChatUtils;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import evernight.mixin.minecraft.world.BlockBehaviourAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import evernight.client.modules.combat.FeetPlace.FeetPlaceModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.RegistrySetSetting;

public class SelfFillModule extends Module {
    protected final Setting<Set<Block>> blocksSetting;
    protected final Setting<Boolean> attackSetting;
    protected final Setting<Boolean> centerSetting;
    protected final Setting<Boolean> headSetting;
    protected final Setting<Boolean> multiPlaceSetting;
    private final Setting<ListFilterMode> selectionSetting;
    private final Setting<ModeChoice> modeSetting;
    private final Setting<Boolean> rotateSetting;
    private final Setting<Boolean> alternativeSetting;
    private final Setting<Boolean> persistentSetting;
    private final Setting<Boolean> jumpDisableSetting;
    public double placementBaselineY;
    private BlockPos obstructionPosition;
    private boolean fillCompleted;
    private boolean skipCenteredTick;

    public SelfFillModule() {
        super("SelfFill", ModuleCategory.COMBAT, "burrow");
        this.blocksSetting = this.add(new RegistrySetSetting<>(
                "Blocks", BuiltInRegistries.BLOCK,
                Blocks.OBSIDIAN, Blocks.ENDER_CHEST));
        this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.WHITELIST));
        this.modeSetting = this.add(new EnumSetting<>("Mode", SelfFillModule.ModeChoice.PLAIN));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
        this.attackSetting = this.add(new BooleanSetting("Attack", true));
        this.centerSetting = this.add(new BooleanSetting("Center", false));
        this.alternativeSetting = this.add(new BooleanSetting("Alternative", false));
        String headKey = "Head";
        this.headSetting = this.add(new BooleanSetting(headKey, false));
        String multiPlaceKey = "MultiPlace";
        Predicate<Object> multiPlaceVisibility = unused -> !this.centerSetting.getValue();
        this.multiPlaceSetting = this.add(new BooleanSetting(multiPlaceKey, true, multiPlaceVisibility));
        Setting<Boolean> registeredPersistent = this.add(new BooleanSetting("Persistent", false).markVisibilityDependency());
        this.persistentSetting = registeredPersistent;
        this.jumpDisableSetting = this.add(new BooleanSetting("JumpDisable", true).dependsOn(registeredPersistent));
        Setting<Boolean> visibilitySetting = this.persistentSetting;
        Predicate<Object> visibilityPredicate = unused -> {
            Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
            return modeSettingSnapshot.getValue() != SelfFillModule.ModeChoice.NO_LAG;
        };
        visibilitySetting.addVisibilityPredicate(visibilityPredicate);
        visibilitySetting = this.jumpDisableSetting;
        visibilityPredicate = unused -> {
            Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
            return modeSettingSnapshot.getValue() != SelfFillModule.ModeChoice.NO_LAG;
        };
        visibilitySetting.addVisibilityPredicate(visibilityPredicate);
        visibilitySetting = this.headSetting;
        Predicate<Boolean> headVisibility = unused -> this.isCobwebWhitelisted();
        visibilitySetting.addVisibilityPredicate(headVisibility);
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable()) {
            this.disable();
        } else {
            if (this.centerSetting.getValue()) {
                FeetPlaceModule.centerPlayer();
                this.skipCenteredTick = true;
            }
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            this.placementBaselineY = player.getY();
            this.fillCompleted = false;
        }
    }

    private int findClearVerticalOffset() {
        BlockPos playerPosition = ClientAccess.evernightMinecraft.player.blockPosition();
        for (int offsetY = -8; offsetY < 8; offsetY++) {
            if (offsetY == 0 || offsetY == 1) {
                continue;
            }
            BlockPos position = playerPosition.offset(0, offsetY, 0);
            var level = ClientAccess.evernightMinecraft.level;
            if (level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
                return offsetY;
            }
        }
        return 999;
    }

    private InventoryUtils.SwapSlot findFillBlockSlot() {
        Predicate<ItemStack> blockFilter = stack -> {
            if (stack.getItem() instanceof BlockItem blockItem) {
                ListFilterMode selection = this.selectionSetting.getValue();
                Block block = blockItem.getBlock();
                return ListFilterMode.matches(selection, block, this.blocksSetting);
            }
            return false;
        };
        return InventoryUtils.findSwapSlot(blockFilter, this.alternativeSetting.getValue());
    }

    private void executeFill(
            Iterable<BlockPos> positions, int previousSlot, InventoryUtils.SwapSlot fillSlot, int verticalOffset) {
        LocalPlayer inventoryPlayer = ClientAccess.evernightMinecraft.player;
        ItemStack fillStack = fillSlot.getStack();
        boolean collidableBlock = this.isCollidableBlock(fillStack);
        boolean clearAbove = true;
        for (BlockPos clearancePosition : positions) {
            int clearanceY = 1;
            while (true) {
                if (clearanceY >= 3) {
                    break;
                }
                ClientLevel level = ClientAccess.evernightMinecraft.level;
                BlockPos abovePosition = clearancePosition.above(clearanceY);
                BlockState aboveState = level.getBlockState(abovePosition);
                if (!aboveState.canBeReplaced()) {
                    clearAbove = false;
                    break;
                }
                clearanceY++;
            }
        }
        if (!collidableBlock) {
            clearAbove = true;
        }
        if (clearAbove) {
            if (!fillSlot.hotbar() && fillSlot.hand() != InteractionHand.OFF_HAND
                    && inventoryPlayer.containerMenu != inventoryPlayer.inventoryMenu) {
                return;
            }
            InventoryUtils.swapTo(fillSlot);
            try {
                Vec3 placementPosition = inventoryPlayer.position();
                if (collidableBlock) {
                    Setting<ModeChoice> modeSettingSnapshot = this.modeSetting;
                    for (double jumpOffset : (modeSettingSnapshot.getValue()).jumpOffsets) {
                        double packetX = ClientAccess.evernightMinecraft.player.getX();
                        double packetY = ClientAccess.evernightMinecraft.player.getY() + jumpOffset;
                        double packetZ = ClientAccess.evernightMinecraft.player.getZ();
                        placementPosition = new Vec3(packetX, packetY, packetZ);
                        float packetYaw = ClientAccess.evernightMinecraft.player.getYRot();
                        float packetPitch = MathConstants.NINETY_DEGREES;
                        PacketSender.sendPositionRotation(packetX, packetY, packetZ, packetYaw, packetPitch, false);
                    }
                }
                LocalPlayer boundsPlayer = ClientAccess.evernightMinecraft.player;
                AABB originalBounds = boundsPlayer.getBoundingBox();
                LocalPlayer emptyBoundsPlayer = ClientAccess.evernightMinecraft.player;
                double width = originalBounds.getXsize();
                double height = originalBounds.getYsize();
                double depth = originalBounds.getZsize();
                AABB placementBounds = new AABB(-width / 2.0D, 0.0D, -depth / 2.0D, width / 2.0D, height, depth / 2.0D)
                        .move(placementPosition);
                emptyBoundsPlayer.setBoundingBox(placementBounds);
                try {
                    for (BlockPos position : positions) {
                        Direction face = BlockInteractionUtils.findSupportDirection(position);
                        if (this.modeSetting.getValue() == SelfFillModule.ModeChoice.NO_LAG) {
                            InteractionHand placementHand = InteractionHand.MAIN_HAND;
                            boolean placementFlag = false;
                            BlockInteractionUtils.placeBlock(position, null, face, placementFlag, placementHand);
                        } else {
                            InteractionHand placementHand = InteractionHand.MAIN_HAND;
                            boolean placementFlag = false;
                            BlockInteractionUtils.placeBlockIgnoringCrystals(position, face, placementFlag, placementHand);
                        }
                    }
                } finally {
                    boundsPlayer.setBoundingBox(originalBounds);
                }
                if (collidableBlock) {
                    double shiftOffset = verticalOffset;
                    this.applyPostFillShift(shiftOffset);
                }
            } finally {
                InventoryUtils.swapBack(previousSlot, fillSlot);
            }
            this.fillCompleted = true;
        }
    }

    @EventHandler
    private void disableAfterJump(LocalPlayerTickEvent event) {
        if (!(ClientAccess.evernightMinecraft.player.getY() > this.placementBaselineY)) {
            return;
        }
        if (!this.persistentSetting.getValue()) {
            return;
        }
        if (!this.jumpDisableSetting.getValue()) {
            return;
        }
        if (this.modeSetting.getValue() != SelfFillModule.ModeChoice.NO_LAG) {
            this.toggle();
        }
    }

    private boolean prepareObstruction(BlockPos position) {
        if (!this.attackSetting.getValue()) {
            return false;
        }
        this.obstructionPosition = null;
        Entity obstruction = BlockInteractionUtils.attackCrystalAt(position, 0);
        ItemStack mainHandStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(position);
        if (!state.canBeReplaced()) {
            double breakDelta = WorldUtils.getBreakProgress(mainHandStack, state, true);
            if (breakDelta >= 1.0D) {
                Action startAction = Action.START_DESTROY_BLOCK;
                Direction startFace = Direction.UP;
                PacketSender.sendPlayerAction(startAction, position, startFace);
                Action stopAction = Action.STOP_DESTROY_BLOCK;
                Direction stopFace = Direction.UP;
                PacketSender.sendPlayerAction(stopAction, position, stopFace);
                ClientContext.worldStateManager.destroyBlockPredicted(position);
                this.obstructionPosition = position;
                return this.obstructionPosition != null;
            }
        }
        if (obstruction != null) {
            this.obstructionPosition = obstruction.blockPosition();
        }
        return this.obstructionPosition != null;
    }

    private Iterable<BlockPos> selectFillPositions(Collection<BlockPos> positions) {
        ArrayList<BlockPos> selectedPositions = new ArrayList<>(positions);
        if (!this.multiPlaceSetting.getValue() || this.centerSetting.getValue()) {
            BlockPos nearestPosition = null;
            double nearestDistanceSquared = 999.0D;
            for (BlockPos position : positions) {
                Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
                Vec3 center = position.getCenter();
                double distanceSquared = playerPosition.distanceToSqr(center);
                if (distanceSquared < nearestDistanceSquared) {
                    nearestPosition = position;
                    nearestDistanceSquared = distanceSquared;
                }
            }
            selectedPositions.clear();
            selectedPositions.add(nearestPosition);
        }
        if (this.headSetting.getValue() && this.headSetting.isVisible()) {
            ArrayList<BlockPos> headPositions = new ArrayList<>();
            for (BlockPos position : selectedPositions) {
                headPositions.add(position.above());
            }
            selectedPositions.addAll(headPositions);
        }
        return selectedPositions;
    }

    private boolean isCobwebWhitelisted() {
        Set<Block> blocks = this.blocksSetting.getValue();
        Block cobweb = Blocks.COBWEB;
        return blocks.contains(cobweb) && this.selectionSetting.getValue() == ListFilterMode.WHITELIST;
    }

    private boolean isCollidableBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return ((BlockBehaviourAccessor) blockItem.getBlock()).isCollidable();
        }
        return false;
    }

    private boolean canFillPosition(BlockPos position) {
        ItemStack mainHandStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        ClientLevel collisionLevel = ClientAccess.evernightMinecraft.level;
        Class<Player> playerType = Player.class;
        AABB collisionBounds = new AABB(position);
        Predicate<Player> otherPlayers = otherPlayer -> otherPlayer != ClientAccess.evernightMinecraft.player;
        List<Player> intersectingPlayers = collisionLevel.getEntitiesOfClass(playerType, collisionBounds, otherPlayers);
        if (!intersectingPlayers.isEmpty()) {
            return false;
        }
        if (!ClientAccess.evernightMinecraft.player.onGround()) {
            return false;
        }
        BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(position);
        boolean replaceable = state.canBeReplaced();
        if (this.attackSetting.getValue()) {
            double breakDelta = WorldUtils.getBreakProgress(mainHandStack, state, true);
            if (breakDelta >= 1.0D) {
                replaceable = true;
            }
        }
        if (!replaceable || !state.getFluidState().isEmpty()) {
            return false;
        }
        return BlockInteractionUtils.findSupportDirection(position) != null;
    }

    private void applyPostFillShift(double verticalOffset) {
        if (this.modeSetting.getValue() == SelfFillModule.ModeChoice.NO_LAG) {
            LocalPlayer movementPlayer = ClientAccess.evernightMinecraft.player;
            double x = ClientAccess.evernightMinecraft.player.getX();
            double y = ClientAccess.evernightMinecraft.player.getY();
            y += 1.0D;
            double z = ClientAccess.evernightMinecraft.player.getZ();
            movementPlayer.setPos(x, y, z);
            ClientAccess.evernightMinecraft.player.setOnGround(true);
        } else {
            if (this.modeSetting.getValue() == SelfFillModule.ModeChoice.PACKET_SHIFT) {
                double packetX = ClientAccess.evernightMinecraft.player.getX();
                double packetY = ClientAccess.evernightMinecraft.player.getY();
                packetY += 1.15D;
                double packetZ = ClientAccess.evernightMinecraft.player.getZ();
                float packetYaw = ClientAccess.evernightMinecraft.player.getYRot();
                float packetPitch = MathConstants.NINETY_DEGREES;
                PacketSender.sendPositionRotation(packetX, packetY, packetZ, packetYaw, packetPitch, false);
                packetX = ClientAccess.evernightMinecraft.player.getX();
                packetY = ClientAccess.evernightMinecraft.player.getY();
                packetZ = ClientAccess.evernightMinecraft.player.getZ();
                packetYaw = ClientAccess.evernightMinecraft.player.getYRot();
                packetPitch = MathConstants.NINETY_DEGREES;
                PacketSender.sendPositionRotation(packetX, packetY, packetZ, packetYaw, packetPitch, false);
            } else {
                double packetX = ClientAccess.evernightMinecraft.player.getX();
                double packetY = ClientAccess.evernightMinecraft.player.getY() + verticalOffset;
                double packetZ = ClientAccess.evernightMinecraft.player.getZ();
                float packetYaw = ClientAccess.evernightMinecraft.player.getYRot();
                float packetPitch = MathConstants.NINETY_DEGREES;
                PacketSender.sendPositionRotation(packetX, packetY, packetZ, packetYaw, packetPitch, false);
            }
        }
    }

    @EventHandler
    private void prepareFillTick(LocalPlayerPreTickEvent event) {
        if (!ClientAccess.evernightMinecraft.player.isPassenger()) {
            if (this.skipCenteredTick) {
                this.skipCenteredTick = false;
            } else {
                AABB playerBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
                double cornerInset = -BoundingBoxUtils.blockCornerInset;
                AABB bounds = playerBounds.inflate(cornerInset);
                double minimumY = bounds.minY;
                AABB feetBounds = bounds.setMaxY(minimumY);
                Stream<BlockPos> boundsPositions = BlockPos.betweenClosedStream(feetBounds);
                boolean multiplePositions = boundsPositions.count() != 1L;
                if (multiplePositions) {
                    if (MovementPhysics.isLocalPlayerMoving()) {
                        return;
                    }
                }
                int verticalOffset = this.findClearVerticalOffset();
                if (verticalOffset != 999) {
                    InventoryUtils.SwapSlot fillSlot = this.findFillBlockSlot();
                    int previousSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
                    if (!fillSlot.isFound()) {
                        String moduleName = this.getDisplayName();
                        MutableComponent message = Component.literal(moduleName);
                        MutableComponent outOfBlocksMessage = message.append(Translations.ui("modules.Placement.OutOfBlocks"));
                        MessageSignature signature = ChatUtils.createSignature(-2);
                        ChatUtils.MessagePriority priority = ChatUtils.MessagePriority.HIGH;
                        ChatUtils.printChat(outOfBlocksMessage, signature, priority);
                        this.disable();
                    } else {
                        Set<BlockPos> positions = PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.evernightMinecraft.player);
                        Predicate<BlockPos> invalidPosition = positionToCheck -> {
                            return !this.canFillPosition(positionToCheck);
                        };
                        positions.removeIf(invalidPosition);
                        if (!positions.isEmpty()) {
                            for (BlockPos position : positions) {
                                if (this.prepareObstruction(position)) {
                                    return;
                                }
                            }
                            float[] initialRotation = new float[2];
                            LocalPlayer yawPlayer = ClientAccess.evernightMinecraft.player;
                            initialRotation[0] = yawPlayer.getYRot();
                            initialRotation[1] = MathConstants.NINETY_DEGREES;
                            float[] rotation = initialRotation;
                            if (this.obstructionPosition != null) {
                                if (this.rotateSetting.getValue()) {
                                    Vec3 targetCenter = this.obstructionPosition.getCenter();
                                    rotation = RotationManager.getRotationsToPosition(targetCenter);
                                }
                            }
                            Runnable fillAction = () -> {
                                Iterable<BlockPos> selectedPositions = this.selectFillPositions(positions);
                                this.executeFill(selectedPositions, previousSlot, fillSlot, verticalOffset);
                            };
                            LockUtils.runWithSharedLock(fillAction);
                            if (this.rotateSetting.getValue()) {
                                ClientContext.rotationManager.requestRotation(rotation, 101);
                            }
                            if (this.fillCompleted) {
                                if (!this.persistentSetting.getValue()) {
                                    this.fillCompleted = false;
                                    this.disable();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void observePositionCorrection(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
            Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
            PositionMoveRotation packetChange = packet.change();
            Vec3 packetPosition = packetChange.position();
            double distanceSquared = playerPosition.distanceToSqr(packetPosition);
            if (distanceSquared > 9.0D) {
                PositionMoveRotation baselineChange = packet.change();
                Vec3 baselinePosition = baselineChange.position();
                this.placementBaselineY = baselinePosition.y();
            }
        }
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain", 0.4, 0.75, 1.01, 1.15),
        PACKET_SHIFT("Shift", 0.4, 0.75, 1.01, 0.99999999),
        NO_LAG("NoLagBack", 0.42, 0.75, 1.0);
        private final String displayName;
        private final double[] jumpOffsets;

        ModeChoice(String displayName, double... jumpOffsets) {
            this.displayName = displayName;
            this.jumpOffsets = jumpOffsets;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}