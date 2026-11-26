package evernight.client.modules.player.Printer;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.esp.FeatureBoxRenderer;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.ListFilterMode;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Prints the active Litematica projection into the world one block per tick.
 *
 * <p>Placement runs on the tick that sends the movement packet, not on the render thread: the
 * rotation request written during {@link RotationRequestEvent} is carried by that same movement
 * packet, so the server resolves orientable blocks with the intended look direction. The preview
 * box is left to {@link FeatureBoxRenderer}.
 */
public class PrinterModule extends Module {
    private static final int ROTATION_PRIORITY = 100;
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final Setting<Boolean> rotateSetting;
    private final Setting<Integer> printingRangeSetting;
    private final Setting<Boolean> inventorySwapSetting;
    private final Setting<Boolean> facingSetting;
    private final Setting<Boolean> facingRedstoneSetting;
    private final Setting<Boolean> facingHalfSetting;
    private final Setting<Boolean> facingPillarsSetting;
    private final Setting<Boolean> facingAttachmentsSetting;
    private final Setting<Boolean> facingChestsSetting;
    private final Setting<Boolean> facingOrientationSetting;
    private final Setting<Boolean> autoSneakSetting;
    private final Setting<Integer> shiftTimeSetting;
    private final Setting<Float> sneakSpeedSetting;
    private final Setting<ListFilterMode> listModeSetting;
    private final Setting<Set<Block>> blacklistSetting;
    private final Setting<Set<Block>> whitelistSetting;
    private final Setting<Boolean> renderSetting;
    private final Setting<PrinterShapeMode> shapeModeSetting;
    private final Setting<Color> lineColorSetting;
    private final Setting<Color> sideColorSetting;
    private final Setting<Float> lineWidthSetting;
    private final Setting<Boolean> fadeSetting;
    private final Setting<Float> fadeTimeSetting;

    private final ElapsedTimer sneakTimer;
    private boolean hasSneak;
    private boolean sneakRequested;
    private PendingPlacement pendingPlacement;

    public PrinterModule() {
        super("Printer", ModuleCategory.PLAYER);
        this.requiresMod("litematica");

        this.rotateSetting = this.add(new BooleanSetting("Rotate", true));
        this.printingRangeSetting = this.add(new NumberSetting<>("PrintingRange", 4, 1, 6).withUnit("m"));
        this.inventorySwapSetting = this.add(new BooleanSetting("InventorySwap", true));

        BooleanSetting facing = new BooleanSetting("Facing", true);
        facing.markVisibilityDependency();
        facing.expanded = true;
        this.facingSetting = this.add(facing);
        Predicate<Boolean> facingChildVisible =
                ignored -> this.facingSetting.isExpanded() && this.facingSetting.getValue();
        this.facingRedstoneSetting = this.add(new BooleanSetting("FacingRedstone", true, facingChildVisible));
        this.facingHalfSetting = this.add(new BooleanSetting("FacingHalf", true, facingChildVisible));
        this.facingPillarsSetting = this.add(new BooleanSetting("FacingPillars", true, facingChildVisible));
        this.facingAttachmentsSetting = this.add(new BooleanSetting("FacingAttachments", true, facingChildVisible));
        this.facingChestsSetting = this.add(new BooleanSetting("FacingChests", true, facingChildVisible));
        this.facingOrientationSetting = this.add(new BooleanSetting("FacingOrientation", true, facingChildVisible));

        BooleanSetting autoSneak = new BooleanSetting("AutoSneak", true);
        autoSneak.markVisibilityDependency();
        autoSneak.expanded = true;
        this.autoSneakSetting = this.add(autoSneak);
        NumberSetting<Integer> shiftTime = new NumberSetting<>("ShiftTime", 100, 0, 1000);
        shiftTime.withUnit("ms");
        this.shiftTimeSetting = this.add(shiftTime.dependsOn(this.autoSneakSetting));
        NumberSetting<Float> sneakSpeed = new NumberSetting<>("SneakSpeed", 20.0F, 0.0F, 20.0F);
        sneakSpeed.withUnit("%");
        this.sneakSpeedSetting = this.add(sneakSpeed.dependsOn(this.autoSneakSetting));

        this.listModeSetting = this.add(new EnumSetting<>("ListMode", ListFilterMode.BLACKLIST));
        Predicate<Set<Block>> blacklistVisible =
                ignored -> this.listModeSetting.getValue() == ListFilterMode.BLACKLIST;
        Predicate<Set<Block>> whitelistVisible =
                ignored -> this.listModeSetting.getValue() == ListFilterMode.WHITELIST;
        this.blacklistSetting = this.add(new RegistrySetSetting<>(
                "Blacklist", BuiltInRegistries.BLOCK, blacklistVisible, new Block[0]));
        this.whitelistSetting = this.add(new RegistrySetSetting<>(
                "Whitelist", BuiltInRegistries.BLOCK, whitelistVisible, new Block[0]));

        BooleanSetting render = new BooleanSetting("Render", true);
        render.markVisibilityDependency();
        render.expanded = true;
        this.renderSetting = this.add(render);
        this.shapeModeSetting = this.add(new EnumSetting<>(
                "ShapeMode", PrinterShapeMode.BOTH, ignored -> this.isRenderVisible()));
        this.lineColorSetting = this.add(new ColorSetting(
                "LineColor", new Color(189, 153, 255, 91),
                ignored -> this.isRenderVisible() && this.shapeModeSetting.getValue() != PrinterShapeMode.SIDES));
        this.sideColorSetting = this.add(new ColorSetting(
                "SideColor", new Color(189, 153, 255, 8),
                ignored -> this.isRenderVisible() && this.shapeModeSetting.getValue() != PrinterShapeMode.LINES));
        this.lineWidthSetting = this.add(new NumberSetting<>(
                "LineWidth", 1.0F, 0.5F, 5.0F,
                ignored -> this.isRenderVisible() && this.shapeModeSetting.getValue() != PrinterShapeMode.SIDES));
        this.fadeSetting = this.add(new BooleanSetting("Fade", true).dependsOn(this.renderSetting));
        NumberSetting<Float> fadeTime = new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F);
        fadeTime.withUnit("s");
        this.fadeTimeSetting = this.add(fadeTime.dependsOn(this.renderSetting, this.fadeSetting));

        this.sneakTimer = new ElapsedTimer();

        FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
        renderer.register(new FeatureBoxRenderer.RenderConfiguration(
                this,
                () -> this.shapeModeSetting.getValue() == PrinterShapeMode.LINES
                        ? TRANSPARENT : this.sideColorSetting.getValue(),
                () -> this.shapeModeSetting.getValue() == PrinterShapeMode.SIDES
                        ? TRANSPARENT : this.lineColorSetting.getValue(),
                () -> this.lineWidthSetting.getValue(),
                () -> this.fadeTimeSetting.getValue(),
                () -> true,
                () -> this.fadeSetting.getValue(),
                1000));
    }

    private boolean isRenderVisible() {
        return this.renderSetting.isExpanded();
    }

    private PrinterFacing.FacingOptions facingOptions() {
        if (!this.facingSetting.getValue()) {
            return PrinterFacing.FacingOptions.NONE;
        }
        return new PrinterFacing.FacingOptions(
                true,
                this.facingRedstoneSetting.getValue(),
                this.facingHalfSetting.getValue(),
                this.facingPillarsSetting.getValue(),
                this.facingAttachmentsSetting.getValue(),
                this.facingChestsSetting.getValue(),
                this.facingOrientationSetting.getValue());
    }

    @Override
    public void onEnable() {
        this.pendingPlacement = null;
        this.hasSneak = false;
        this.sneakRequested = false;
    }

    @Override
    public void onDisable() {
        this.pendingPlacement = null;
        this.sneakRequested = false;
        this.stopSneak();
    }

    /**
     * Chooses the block to place and writes its rotation request. The actual click is sent in
     * {@link #onSendPosition} after the movement packet carried that rotation to the server.
     */
    @EventHandler
    private void onRotationRequest(RotationRequestEvent event) {
        this.preparePlacement();
    }

    @EventHandler
    private void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (event.getStage() != EventStage.Post) {
            return;
        }
        this.executePlacement(event);
    }

    @EventHandler
    private void onLocalPlayerTick(LocalPlayerTickEvent event) {
        if (!this.hasSneak) {
            return;
        }
        if (!this.autoSneakSetting.getValue()) {
            this.stopSneak();
            return;
        }
        // Keep at least a few ticks so a zero ShiftTime can still reach the placement phase.
        long timeout = Math.max(this.shiftTimeSetting.getValue() * 2L, 150L);
        if (this.sneakTimer.hasElapsed(timeout)) {
            this.stopSneak();
        }
    }

    /**
     * Optional movement override while the background sneak is active. The default speed 0 keeps the
     * player still, which is the safest option for precise printing; raising it lets the printer walk
     * while sneaking.
     */
    @EventHandler
    private void onLocalPlayerMove(LocalPlayerMoveEvent event) {
        if (!this.hasSneak) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Input input = player.input.keyPresses;
        if (!input.forward() && !input.backward() && !input.left() && !input.right()) {
            event.setHorizontalVelocity(0.0D, 0.0D);
            return;
        }
        double speed = MovementPhysics.baseMoveSpeed * this.sneakSpeedSetting.getValue() / 100.0D;
        double[] motion = MovementPhysics.getHorizontalMotion(player.getYRot(), player.input, speed);
        event.setHorizontalVelocity(motion[0], motion[1]);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundOpenScreenPacket openScreen)) {
            return;
        }
        PacketSender.send(new ServerboundContainerClosePacket(openScreen.getContainerId()));
        event.cancel();
    }

    private void preparePlacement() {
        this.pendingPlacement = null;
        this.sneakRequested = false;
        if (this.isWorldUnavailable() || ClientAccess.evernightMinecraft.screen != null) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player.isSpectator() || !player.isAlive() || player.isHandsBusy() || player.isSleeping()) {
            return;
        }
        if (this.hasSneak && this.autoSneakSetting.getValue()
                && !this.sneakTimer.hasElapsed(this.shiftTimeSetting.getValue())) {
            return;
        }
        if (!ClientContext.projectionSource.isAvailable()) {
            return;
        }

        float range = this.printingRangeSetting.getValue();
        Stream<BlockPos> positions = WorldUtils.streamSphereBlocks(player.position(), range);
        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext() && this.pendingPlacement == null && !this.sneakRequested) {
            PendingPlacement placement = this.findPlacementAt(iterator.next());
            if (placement == null) {
                continue;
            }
            this.pendingPlacement = placement;
            if (placement.angles() != null) {
                ClientContext.rotationManager.requestRotation(placement.angles(), ROTATION_PRIORITY);
            }
            if (this.renderSetting.getValue()) {
                ClientContext.featureBoxRenderer.addBox(this, placement.position());
            }
        }
    }

    private @Nullable PendingPlacement findPlacementAt(BlockPos position) {
        BlockState required = ClientContext.projectionSource.getProjectedStateIfRendered(position);
        if (required == null || required.isAir()) {
            return null;
        }
        BlockState current = ClientAccess.evernightMinecraft.level.getBlockState(position);
        if (current.getBlock() == required.getBlock()) {
            return null;
        }
        if (!current.canBeReplaced()) {
            return null;
        }
        if (!this.matchesFilter(required)) {
            return null;
        }
        if (!BlockInteractionUtils.canPlaceBlock(position, required.getBlock(), false)) {
            return null;
        }
        if (required.getBlock() instanceof RedStoneWireBlock && !required.canSurvive(
                ClientAccess.evernightMinecraft.level, position)) {
            return null;
        }
        Item requiredItem = required.getBlock().asItem();
        if (requiredItem == Items.AIR) {
            return null;
        }
        InventoryUtils.SwapSlot slot = this.findSlot(requiredItem);
        if (!slot.isFound()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        SideChoice side = this.findSide(position, required, stack, this.facingOptions());
        if (side == null) {
            return null;
        }

        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        boolean needsSneak = BlockInteractionUtils.requiresSneakForInteraction(side.neighbor());
        if (needsSneak && !player.isShiftKeyDown()) {
            if (!this.autoSneakSetting.getValue()) {
                return null;
            }
            if (!this.hasSneak) {
                this.startSneak();
                this.sneakRequested = true;
                return null;
            }
        }
        return new PendingPlacement(
                position, required, side.side(), side.clickedFace(), side.neighbor(), side.hit(), side.angles());
    }

    private @Nullable SideChoice findSide(
            BlockPos position, BlockState required, ItemStack stack, PrinterFacing.FacingOptions options) {
        // A second half of a double chest must click the first chest's side face while sneaking.
        // Do this before the generic preference pass so a non-merging support is never chosen.
        SideChoice chestSide = this.findChestPartnerSide(position, required, stack, options);
        if (chestSide != null) {
            return chestSide;
        }
        // Prefer supports that do not need a background sneak; only fall back to a sneaking side
        // when the projection cannot be placed otherwise.
        SideChoice preferred = this.findSidePass(position, required, stack, options, true);
        return preferred != null ? preferred : this.findSidePass(position, required, stack, options, false);
    }

    private @Nullable SideChoice findChestPartnerSide(
            BlockPos position, BlockState required, ItemStack stack, PrinterFacing.FacingOptions options) {
        if (!options.master() || !options.chest()) {
            return null;
        }
        if (!required.hasProperty(BlockStateProperties.CHEST_TYPE)
                || required.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE
                || !required.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return null;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() + 0.5D;
        Direction facing = required.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (Direction side : Direction.values()) {
            // Clicking the front/back of the partner does not merge the chest; only its side face
            // (the axis perpendicular to the chest facing) does.
            if (!side.getAxis().isHorizontal() || side.getAxis() == facing.getAxis()) {
                continue;
            }
            BlockPos neighbor = position.relative(side);
            BlockState neighborState = ClientAccess.evernightMinecraft.level.getBlockState(neighbor);
            if (neighborState.getBlock() != required.getBlock()
                    || !neighborState.hasProperty(BlockStateProperties.CHEST_TYPE)
                    || neighborState.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE
                    || !neighborState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    || neighborState.getValue(BlockStateProperties.HORIZONTAL_FACING) != facing) {
                continue;
            }
            Vec3 hit = PrinterFacing.hitPosition(position, side, required, options.half());
            if (hit == null || eye.distanceTo(hit) > reach) {
                continue;
            }
            Direction clickedFace = side.getOpposite();
            if (!WorldUtils.canSee(eye, hit, neighbor, clickedFace)) {
                continue;
            }
            float[] angles = this.rotateSetting.getValue()
                    ? RotationManager.calculateRotations(eye, hit) : null;
            return new SideChoice(side, clickedFace, neighbor, hit, angles);
        }
        return null;
    }

    private @Nullable SideChoice findSidePass(
            BlockPos position, BlockState required, ItemStack stack,
            PrinterFacing.FacingOptions options, boolean avoidSneak) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() + 0.5D;
        Direction facing = PrinterFacing.getFacing(required);
        boolean redstoneRotation = options.master() && options.redstone()
                && this.rotateSetting.getValue() && facing != null
                && PrinterFacing.isRedstoneComponent(required);

        for (Direction side : Direction.values()) {
            BlockPos neighbor = position.relative(side);
            if (!WorldUtils.hasCollision(neighbor)) {
                continue;
            }
            boolean needsSneak = BlockInteractionUtils.requiresSneakForInteraction(neighbor);
            if (needsSneak && !player.isShiftKeyDown()) {
                if (avoidSneak) {
                    continue;
                }
                if (!this.autoSneakSetting.getValue()) {
                    continue;
                }
            }
            Vec3 hit = PrinterFacing.hitPosition(position, side, required, options.half());
            if (hit == null || eye.distanceTo(hit) > reach) {
                continue;
            }
            Direction clickedFace = side.getOpposite();
            if (!WorldUtils.canSee(eye, hit, neighbor, clickedFace)) {
                continue;
            }
            BlockHitResult hitResult = new BlockHitResult(hit, clickedFace, neighbor, false);
            if (redstoneRotation) {
                Direction look = PrinterFacing.usesSameLookDirection(required)
                        ? facing : facing.getOpposite();
                return new SideChoice(
                        side, clickedFace, neighbor, hit, PrinterFacing.rotationsForDirection(look));
            }
            if (this.rotateSetting.getValue()) {
                Vec3 aim = hit.subtract(eye);
                BlockState simulated = PrinterFacing.simulate(
                        required, ClientAccess.evernightMinecraft.level, player,
                        InteractionHand.MAIN_HAND, stack, hitResult, aim);
                if (simulated == null || !PrinterFacing.matches(required, simulated, options)) {
                    continue;
                }
                return new SideChoice(
                        side, clickedFace, neighbor, hit, RotationManager.calculateRotations(eye, hit));
            }
            BlockState simulated = PrinterFacing.simulate(
                    required, ClientAccess.evernightMinecraft.level, player,
                    InteractionHand.MAIN_HAND, stack, hitResult, null);
            if (simulated == null || !PrinterFacing.matches(required, simulated, options)) {
                continue;
            }
            return new SideChoice(side, clickedFace, neighbor, hit, null);
        }
        return null;
    }

    private void executePlacement(LocalPlayerSendPositionEvent event) {
        PendingPlacement placement = this.pendingPlacement;
        this.pendingPlacement = null;
        if (placement == null || this.isWorldUnavailable()) {
            return;
        }
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        if (player.isSpectator() || !player.isAlive() || player.isHandsBusy() || player.isSleeping()) {
            return;
        }
        if (ClientAccess.evernightMinecraft.gameMode == null) {
            return;
        }
        if (this.hasSneak && this.autoSneakSetting.getValue()
                && !this.sneakTimer.hasElapsed(this.shiftTimeSetting.getValue())) {
            return;
        }
        if (placement.angles() != null && !RotationManager.isSilentRotationMode()) {
            float yawDifference = Mth.degreesDifferenceAbs(event.getCurrentYaw(), placement.angles()[0]);
            float pitchDifference = Math.abs(event.getCurrentPitch() - placement.angles()[1]);
            if (yawDifference > 1.5F || pitchDifference > 1.5F) {
                return;
            }
        }

        BlockPos position = placement.position();
        BlockState current = ClientAccess.evernightMinecraft.level.getBlockState(position);
        if (current.getBlock() == placement.required().getBlock() || !current.canBeReplaced()) {
            return;
        }
        if (!BlockInteractionUtils.canPlaceBlock(position, placement.required().getBlock(), false)) {
            return;
        }
        if (!WorldUtils.hasCollision(placement.neighbor())) {
            return;
        }
        if (placement.hit().distanceTo(player.getEyePosition()) > player.blockInteractionRange() + 0.5D) {
            return;
        }
        boolean needsSneak = BlockInteractionUtils.requiresSneakForInteraction(placement.neighbor());
        if (needsSneak && !player.isShiftKeyDown() && !this.hasSneak) {
            return;
        }
        if (placement.required().getBlock() instanceof RedStoneWireBlock && !placement.required().canSurvive(
                ClientAccess.evernightMinecraft.level, position)) {
            return;
        }

        Item requiredItem = placement.required().getBlock().asItem();
        InventoryUtils.SwapSlot slot = this.findSlot(requiredItem);
        if (!slot.isFound()) {
            return;
        }
        int previousSlot = InventoryUtils.getSelectedSlot();
        try {
            InventoryUtils.swapTo(slot);
            ItemStack heldStack = slot.hand() == InteractionHand.OFF_HAND
                    ? player.getOffhandItem() : player.getMainHandItem();
            if (!heldStack.is(requiredItem)) {
                return;
            }
            this.sendPlacement(slot, placement);
        } finally {
            InventoryUtils.swapBack(previousSlot, slot);
            this.stopSneak();
        }
    }

    private void sendPlacement(InventoryUtils.SwapSlot slot, PendingPlacement placement) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
        if (gameMode == null) {
            return;
        }
        BlockHitResult hitResult = new BlockHitResult(
                placement.hit(), placement.clickedFace(), placement.neighbor(), false);
        InteractionHand hand = slot.hand();
        InteractionResult result;
        boolean wasPlacing = BlockInteractionUtils.isPlacingBlock;
        BlockInteractionUtils.isPlacingBlock = true;
        try {
            result = gameMode.useItemOn(player, hand, hitResult);
        } finally {
            BlockInteractionUtils.isPlacingBlock = wasPlacing;
        }
        if (result instanceof InteractionResult.Success success
                && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
            player.swing(hand);
        }
        if (result.consumesAction() && this.renderSetting.getValue()) {
            ClientContext.featureBoxRenderer.addBox(this, placement.position());
        }
    }

    private InventoryUtils.SwapSlot findSlot(Item item) {
        if (this.inventorySwapSetting.getValue()) {
            return InventoryUtils.findSwapSlot(item, true);
        }
        int slot = InventoryUtils.findHotbarSlot(item);
        return new InventoryUtils.SwapSlot(slot, true, InteractionHand.MAIN_HAND);
    }

    private boolean matchesFilter(BlockState required) {
        ListFilterMode mode = this.listModeSetting.getValue();
        if (mode == ListFilterMode.WHITELIST) {
            return this.whitelistSetting.getValue().contains(required.getBlock());
        }
        if (mode == ListFilterMode.BLACKLIST) {
            return !this.blacklistSetting.getValue().contains(required.getBlock());
        }
        return true;
    }

    private void startSneak() {
        if (this.hasSneak || ClientAccess.evernightMinecraft.player.isShiftKeyDown()) {
            return;
        }
        ClientContext.playerStateTracker.setSneaking(true);
        this.hasSneak = true;
        this.sneakTimer.reset();
    }

    private void stopSneak() {
        if (!this.hasSneak) {
            return;
        }
        this.hasSneak = false;
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        // Keep a real Shift press intact; only release the background sneak we sent ourselves.
        if (player != null && !player.isShiftKeyDown()) {
            ClientContext.playerStateTracker.setSneaking(false);
        }
    }

    private record SideChoice(
            Direction side, Direction clickedFace, BlockPos neighbor, Vec3 hit, float[] angles) {
    }

    private record PendingPlacement(
            BlockPos position, BlockState required, Direction side, Direction clickedFace,
            BlockPos neighbor, Vec3 hit, @Nullable float[] angles) {
    }
}
