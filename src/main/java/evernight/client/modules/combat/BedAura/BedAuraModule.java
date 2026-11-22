package evernight.client.modules.combat.BedAura;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.player.MovementPredictionManager;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.esp.FeatureBoxRenderer;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.DimensionType;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Iterator;
import java.util.function.Predicate;

public final class BedAuraModule extends Module {
    final Setting<Float> placeRangeSetting;
    final Setting<Boolean> rotateSetting;
    final Setting<Boolean> liquidPlaceSetting;
    final Setting<Boolean> airPlaceSetting;
    final Setting<Boolean> breakSetting;
    final Setting<Float> breakRangeSetting;
    final Setting<Boolean> tpsSyncSetting;
    final Setting<Boolean> instantSetting;
    final Setting<Float> minDamageSetting;
    final Setting<Float> maxSelfDamageSetting;
    final Setting<Boolean> antiSuicideSetting;
    private final Setting<Boolean> placeSetting;
    private final Setting<Integer> placeDelaySetting;
    private final Setting<Integer> breakDelaySetting;
    private final Setting<Boolean> extrapolationSetting;
    private final Setting<Integer> ticksSetting;
    private final Setting<Boolean> refillSetting;
    private final Setting<Integer> slotSetting;
    private final Setting<Boolean> renderSetting;
    private final Setting<Color> fillSetting;
    private final Setting<Color> outlineSetting;
    private final Setting<Boolean> fadeSetting;
    private final Setting<Float> fadeTimeSetting;
    private final Setting<Boolean> targetingSetting;
    private final BedPlacementScanner placementScanner;
    private final BedBreakScanner breakScanner;
    private final ElapsedTimer placeTimer;
    private final ElapsedTimer breakTimer;
    private Player target;
    private BedPlacementCandidate placement;
    private BlockPos breakPosition;

    public BedAuraModule() {
        super("BedAura", ModuleCategory.COMBAT);
        this.placeSetting = this.add(new BooleanSetting("Place", true).markVisibilityDependency());
        this.placeDelaySetting = this.add(new NumberSetting<>("Delay", 1, 1, 20).dependsOn(this.placeSetting));
        this.placeRangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 0.1F, 6.0F).dependsOn(this.placeSetting));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", true).dependsOn(this.placeSetting));
        this.liquidPlaceSetting = this.add(new BooleanSetting("LiquidPlace", false).dependsOn(this.placeSetting));
        this.airPlaceSetting = this.add(new BooleanSetting("AirPlace", false).dependsOn(this.placeSetting));
        this.breakSetting = this.add(new BooleanSetting("Break", true).markVisibilityDependency());
        this.breakDelaySetting = this.add(new NumberSetting<>("Delay", 1, 1, 20).dependsOn(this.breakSetting));
        this.breakRangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 0.1F, 6.0F).dependsOn(this.breakSetting));
        this.tpsSyncSetting = this.add(new BooleanSetting("TPSSync", false).dependsOn(this.breakSetting));
        this.instantSetting = this.add(new BooleanSetting("Instant", true).dependsOn(this.breakSetting));
        this.extrapolationSetting = this.add(new BooleanSetting("Extrapolation", false).markVisibilityDependency());
        this.ticksSetting = this.add(new NumberSetting<>("Ticks", 3, 1, 8).dependsOn(this.extrapolationSetting));
        this.refillSetting = this.add(new BooleanSetting("Refill", false).markVisibilityDependency());
        this.slotSetting = this.add(new NumberSetting<>("Slot", 1, 1, 9).dependsOn(this.refillSetting));
        this.renderSetting = this.add(new BooleanSetting("Render", true).markVisibilityDependency());
        this.fillSetting = this.add(new ColorSetting("Fill", new Color(1136499199, true)).dependsOn(this.renderSetting));
        this.outlineSetting = this.add(new ColorSetting("Outline", new Color(-910321153, true)).dependsOn(this.renderSetting));
        this.fadeSetting = this.add(new BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting));
        this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 0.5F, 0.1F, 1.0F).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting));
        this.targetingSetting = this.add(new BooleanSetting("Targeting", false).markAsGroup().markVisibilityDependency());
        this.minDamageSetting = this.add(new NumberSetting<>("MinDamage", 6.0F, 0.0F, 12.0F).dependsOn(this.targetingSetting));
        this.maxSelfDamageSetting = this.add(new NumberSetting<>("MaxSelfDamage", 10.0F, 0.0F, 20.0F).dependsOn(this.targetingSetting));
        this.antiSuicideSetting = this.add(new BooleanSetting("AntiSucide", true).dependsOn(this.targetingSetting));
        this.placementScanner = new BedPlacementScanner(this);
        this.breakScanner = new BedBreakScanner(this);
        this.placeTimer = new ElapsedTimer();
        this.breakTimer = new ElapsedTimer();
        FeatureBoxRenderer renderer = ClientContext.featureBoxRenderer;
        FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
                this, this.fillSetting, this.outlineSetting, () -> {
            return 1.0F;
        }, this.fadeTimeSetting, () -> false, this.fadeSetting, 1000);
        renderer.register(renderConfiguration);
        this.placeRangeSetting.setConfigName("PlaceRange");
        this.breakRangeSetting.setConfigName("BreakRange");
        this.placeDelaySetting.setConfigName("PlaceDelay");
        this.breakDelaySetting.setConfigName("BreakDelay");
    }

    private static Direction selectInteractionDirection(BlockPos position, Direction fallbackDirection) {
        BlockInteractionUtils.getStrictDirections(position);
        return fallbackDirection;
    }

    private void refillBeds() {
        if (!this.refillSetting.getValue()) {
            return;
        }
        int refillSlot = this.slotSetting.getValue() - 1;
        ItemStack currentStack = ClientAccess.evernightMinecraft.player.getInventory().getItem(refillSlot);
        if (!(currentStack.getItem() instanceof BedItem)) {
            Predicate<ItemStack> bedPredicate = candidateStack -> {
                return candidateStack.getItem() instanceof BedItem;
            };
            int bedSlot = InventoryUtils.findInventorySlot(bedPredicate, true);
            if (bedSlot != -1) {
                InventoryUtils.swapWithHotbarSlot(bedSlot, refillSlot);
            }
        }
    }

    private void breakBed(BlockPos position) {
        if (position == null) {
            return;
        }
        Direction fallbackSnapshot = Direction.UP;
        Direction direction = selectInteractionDirection(position, fallbackSnapshot);
        BlockHitResult hit = new BlockHitResult(position.getCenter(), direction, position, false);
        PlayerStateTracker interactionScheduler = ClientContext.playerStateTracker;
        Runnable interactionCallback = () -> {
            MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
            LocalPlayer interactionPlayer = ClientAccess.evernightMinecraft.player;
            InteractionHand interactionHand = InteractionHand.MAIN_HAND;
            gameMode.useItemOn(interactionPlayer, interactionHand, hit);
            LocalPlayer swingPlayer = ClientAccess.evernightMinecraft.player;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            swingPlayer.swing(swingHand);
        };
        interactionScheduler.runWithoutSneaking(interactionCallback);
        Plane horizontalPlane = Plane.HORIZONTAL;
        Iterator<Direction> directions = horizontalPlane.iterator();
        while (directions.hasNext()) {
            Direction adjacentDirection = directions.next();
            BlockPos adjacentPosition = position.relative(adjacentDirection);
            BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(adjacentPosition);
            if (state.getBlock() instanceof BedBlock) {
                ClientContext.worldStateManager.destroyBlockPredicted(adjacentPosition);
                break;
            }
        }
        ClientContext.worldStateManager.destroyBlockPredicted(position);
        this.breakTimer.reset();
    }

    @Override
    public String getInfo() {
        if (this.target == null) {
            return null;
        }
        return this.target.getGameProfile().name();
    }

    private boolean hasActionDelayElapsed(ElapsedTimer timer, Setting<Integer> delaySettingSnapshot) {
        Boolean synchronize = this.tpsSyncSetting.getValue();
        float factorSnapshot;
        if (synchronize) {
            factorSnapshot = ClientContext.serverStateManager.getTickRateScale();
        } else {
            factorSnapshot = 1.0F;
        }
        Integer delayTicks = delaySettingSnapshot.getValue();
        return timer.hasElapsed(((long) ((float) (50L * delayTicks.intValue()) / factorSnapshot)));
    }

    private void placeWithBedSlot(BedPlacementCandidate placement) {
        Predicate<ItemStack> bedPredicate = candidateStack -> {
            return candidateStack.getItem() instanceof BedItem;
        };
        int bedSlot = InventoryUtils.findHotbarSlot(bedPredicate);
        int originalSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        if (bedSlot != -1) {
            InventoryUtils.selectHotbarSlot(bedSlot);
            this.placeBed(placement);
            InventoryUtils.selectHotbarSlot(originalSlot);
        }
    }

    boolean isBedPart(BlockPos position, BedPart part) {
        BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(position);
        if (state.getBlock() instanceof BedBlock) {
            EnumProperty partProperty = BedBlock.PART;
            return state.getValue(partProperty) == part;
        }
        return false;
    }

    private Player selectTarget() {
        Player closestTarget = null;
        float closestDistance = 99999.0F;
        Iterator<? extends Player> players = ClientAccess.evernightMinecraft.level.players().iterator();
        while (players.hasNext()) {
            Player candidate = players.next();
            if (candidate != ClientAccess.evernightMinecraft.player) {
                if (!ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                    double distanceLimit = closestDistance;
                    Vec3 comparisonEyeSnapshot = ClientAccess.evernightMinecraft.player.getEyePosition();
                    Vec3 comparisonPosition = candidate.position();
                    if (distanceLimit > comparisonEyeSnapshot.distanceTo(comparisonPosition)) {
                        closestTarget = candidate;
                        closestDistance = (float) ClientAccess.evernightMinecraft.player.getEyePosition().distanceTo(candidate.position());
                    }
                }
            }
        }
        return closestTarget;
    }

    public AABB getDamageBounds(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (this.extrapolationSetting.getValue()) {
                MovementPredictionManager extrapolatorSnapshot = ClientContext.movementPredictionManager;
                int extrapolationTicks = this.ticksSetting.getValue();
                return extrapolatorSnapshot.getPredictedBoundingBox(player, extrapolationTicks);
            }
        }
        return entity.getBoundingBox();
    }

    @EventHandler
    private void onPlayerUpdate(RotationRequestEvent event) {
        DimensionType dimension = WorldUtils.getDimension();
        if (dimension.isOverworld()) {
            return;
        }
        this.target = this.selectTarget();
        if (this.target == null) {
            this.placement = null;
        } else {
            BedPlacementScanner placementScannerSnapshot = this.placementScanner;
            Player placementTargetSnapshot = this.target;
            this.placement = placementScannerSnapshot.scanPlacement(placementTargetSnapshot);
            BedBreakScanner breakScannerSnapshot = this.breakScanner;
            Player breakTargetSnapshot = this.target;
            this.breakPosition = breakScannerSnapshot.scanBreakPosition(breakTargetSnapshot);
        }
        if (this.placement != null) {
            this.refillBeds();
            if (this.breakSetting.getValue()) {
                Setting<Integer> breakDelaySetting = this.breakDelaySetting;
                ElapsedTimer breakDelayTimer = this.breakTimer;
                if (this.hasActionDelayElapsed(breakDelayTimer, breakDelaySetting)) {
                    BlockPos breakPositionSnapshot = this.breakPosition;
                    this.breakBed(breakPositionSnapshot);
                }
            }
            if (this.placeSetting.getValue()) {
                Setting<Integer> placeDelaySetting = this.placeDelaySetting;
                ElapsedTimer placeDelayTimer = this.placeTimer;
                if (this.hasActionDelayElapsed(placeDelayTimer, placeDelaySetting)) {
                    BedPlacementCandidate placementSnapshot = this.placement;
                    this.placeWithBedSlot(placementSnapshot);
                }
            }
        }
    }

    private void placeBed(BedPlacementCandidate placement) {
        if (placement != null) {
            BlockPos footSnapshot = placement.foot();
            if (BlockInteractionUtils.canPlaceAt(footSnapshot, true)) {
                BlockHitResult placementHit = placement.hitResult();
                Vec3 hitPoint = placementHit.getLocation();
                if (hitPoint == null) {
                    BlockPos fallbackFoot = placement.foot();
                    fallbackFoot.getCenter();
                }
                Boolean rotate = this.rotateSetting.getValue();
                float[] angles;
                if (rotate && hitPoint != null) {
                    angles = RotationManager.getRotationsToPosition(hitPoint);
                } else {
                    BlockPos directionFoot = placement.foot();
                    Vec3 footCenterSnapshot = directionFoot.getCenter();
                    BlockPos directionHead = placement.head();
                    Vec3 headCenterSnapshot = directionHead.getCenter();
                    angles = RotationManager.calculateRotations(footCenterSnapshot, headCenterSnapshot);
                }
                ClientContext.rotationManager.requestRotation(angles, 250);
                BlockHitResult hit = placement.hitResult();
                PlayerStateTracker placementScheduler = ClientContext.playerStateTracker;
                Runnable placementCallback = () -> {
                    InteractionHand interactionHand = InteractionHand.MAIN_HAND;
                    PacketSender.sendUseItemOn(interactionHand, hit);
                };
                placementScheduler.runSneaking(placementCallback);
                LocalPlayer swingPlayer = ClientAccess.evernightMinecraft.player;
                InteractionHand swingHand = InteractionHand.MAIN_HAND;
                swingPlayer.swing(swingHand);
                footSnapshot = placement.foot();
                BlockPos boundsHead = placement.head();
                AABB renderBounds = AABB.encapsulatingFullBlocks(footSnapshot, boundsHead);
                double minimumY = renderBounds.minY;
                double maximumY = minimumY + 0.5625D;
                renderBounds = renderBounds.setMaxY(maximumY);
                renderBounds = renderBounds.inflate(0.002F);
                if (this.renderSetting.getValue()) {
                    ClientContext.featureBoxRenderer.addBox(this, renderBounds);
                }
                if (this.breakSetting.getValue()) {
                    if (this.instantSetting.getValue()) {
                        PlayerStateTracker breakScheduler = ClientContext.playerStateTracker;
                        Runnable breakCallback = () -> {
                            BlockPos instantFootSnapshot = placement.foot();
                            Direction instantFallbackSnapshot = Direction.UP;
                            Direction instantDirection = selectInteractionDirection(instantFootSnapshot, instantFallbackSnapshot);
                            BlockHitResult instantHit = new BlockHitResult(
                                    placement.foot().getCenter(), instantDirection, placement.foot(), false);
                            InteractionHand instantInteractionHand = InteractionHand.MAIN_HAND;
                            PacketSender.sendUseItemOn(instantInteractionHand, instantHit);
                            LocalPlayer instantSwingPlayer = ClientAccess.evernightMinecraft.player;
                            InteractionHand instantSwingHand = InteractionHand.MAIN_HAND;
                            instantSwingPlayer.swing(instantSwingHand);
                        };
                        breakScheduler.runWithoutSneaking(breakCallback);
                    }
                }
                this.placeTimer.reset();
            }
        }
    }
}