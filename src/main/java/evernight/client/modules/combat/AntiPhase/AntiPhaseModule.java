package evernight.client.modules.combat.AntiPhase;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.exploit.MultiTask.MultiTaskModule;
import evernight.client.render.esp.FeatureBoxRenderer;
import evernight.client.settings.options.AntiPhaseMaterialChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateTracker;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.ColorSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class AntiPhaseModule extends Module {
    private static MultiTaskModule multiTask;

    static {
        multiTask = ClientContext.getModule(MultiTaskModule.class);
    }

    public final Setting<Boolean> bannerSetting;
    protected final Setting<Integer> delaySetting;
    protected final Setting<Integer> blocksPerTickSetting;
    protected final Setting<Boolean> rotateSetting;
    protected final Setting<Boolean> noCrawlSetting;
    protected final Setting<Boolean> smartSetting;
    protected final Setting<Boolean> renderSetting;
    protected final Setting<Color> fillSetting;
    protected final Setting<Color> outlineSetting;
    protected final Setting<Float> lineWidthSetting;
    protected final Setting<Boolean> fadeSetting;
    protected final Setting<Float> fadeTimeSetting;
    final Setting<AntiPhaseMaterialChoice> materialSetting;
    private final Setting<Float> rangeSetting;
    private final ElapsedTimer placementTimer;
    private AntiPhaseModule.PendingSwap pendingSwap;
    private boolean useBanner;

    public AntiPhaseModule() {
        super("AntiPhase", ModuleCategory.COMBAT);
        this.materialSetting = this.add(new EnumSetting<>("Material", AntiPhaseMaterialChoice.LADDER));
        Predicate bannerVisibility = unused -> this.materialSetting.getValue() == AntiPhaseMaterialChoice.ITEMFRAME;
        this.bannerSetting = this.add(new BooleanSetting("Banner", false, bannerVisibility));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 0.1F, 6.0F));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 100, 50, 250).withUnit("ms"));
        this.blocksPerTickSetting = this.add(new NumberSetting<>("BPT", 6, 1, 6));
        this.rotateSetting = this.add(new BooleanSetting("Rotate", false));
        this.noCrawlSetting = this.add(new BooleanSetting("NoCrawl", false));
        this.smartSetting = this.add(new BooleanSetting("Smart", false));
        this.renderSetting = this.add(new BooleanSetting("Render", false).markVisibilityDependency());
        Predicate fillVisibility = unused -> this.renderSetting.isExpanded();
        this.fillSetting = this.add(new ColorSetting("Fill", new Color(146643455, true), fillVisibility));
        Predicate outlineVisibility = unused -> this.renderSetting.isExpanded();
        this.outlineSetting = this.add(new ColorSetting("Outline", new Color(1539152383, true), outlineVisibility));
        this.lineWidthSetting = this.add(new NumberSetting<>("LineWidth", 1.0F, 0.1F, 3.0F).dependsOn(this.renderSetting));
        this.fadeSetting = this.add(new BooleanSetting("Fade", true).dependsOn(this.renderSetting));
        this.fadeTimeSetting = this.add(new NumberSetting<>("FadeTime", 1.0F, 0.1F, 1.0F).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting));
        this.placementTimer = new ElapsedTimer();
        this.pendingSwap = null;
        FeatureBoxRenderer rendererSnapshot = ClientContext.featureBoxRenderer;
        FeatureBoxRenderer.RenderConfiguration renderConfiguration = new FeatureBoxRenderer.RenderConfiguration(
                this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting,
                () -> true, this.fadeSetting, 450);
        rendererSnapshot.register(renderConfiguration);
    }

    public static void bindMultiTaskModule(ModuleRegistry registry) {
        multiTask = registry.getModule(MultiTaskModule.class);
    }

    protected AntiPhasePlacementPlanner selectPlacementPlanner() {
        AntiPhaseMaterialChoice material = this.materialSetting.getValue();
        return switch (material) {
            case LADDER -> new AntiPhaseLadderPlanner();
            case SCAFFOLDING -> new AntiPhaseFullBlockPlanner();
            case VINE -> new AntiPhaseVinePlanner();
            case ITEMFRAME -> this.selectItemFramePlanner();
        };
    }

    protected boolean shouldUseBanner(Player target) {
        BlockPos targetPosition = PlayerStateUtils.getRoundedBlockPosition(target);
        if (this.bannerSetting.getValue()) {
            return !(WorldUtils.getBlock(targetPosition) instanceof AbstractBannerBlock);
        }
        return false;
    }

    protected AntiPhasePlacementPlanner selectItemFramePlanner() {
        return this.useBanner ? new AntiPhaseFullBlockPlanner() : new AntiPhaseItemFramePlanner();
    }

    protected boolean isValidTarget(Player target) {
        if (target.isAlive() && ClientAccess.evernightMinecraft.player != target) {
            if (!target.isSwimming()) {
                if (ClientContext.socialRegistry.isFriendPlayer(target)) {
                    return false;
                }
                Set<BlockPos> occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(target);
                if (occupiedPositions.size() != 1) {
                    return false;
                }
                BlockPos roundedPosition = PlayerStateUtils.getRoundedBlockPosition(
                        target);
                if (this.hasItemFrame(roundedPosition)) {
                    return false;
                }
                if (!PlayerStateUtils.hasArmor(target)) {
                    return false;
                }
                if (this.noCrawlSetting.getValue()) {
                    double targetHeight = target.getBoundingBox().getYsize();
                    if (targetHeight <= 1.0D) {
                        return false;
                    }
                }
                if (this.smartSetting.getValue()) {
                    double targetSpeed = MovementPhysics.getHorizontalSpeedKmh(target);
                    if (targetSpeed > 18.0D) {
                        return false;
                    }
                    AABB expandedBounds = target.getBoundingBox().inflate(0.08D, 0.0, 0.08D);
                    if (ClientAccess.evernightMinecraft.level.noBlockCollision(target, expandedBounds)) {
                        return false;
                    }
                    if (!EntityUtils.hasCollisionBelow(target)) {
                        return false;
                    }
                }
                float distance = target.distanceTo(ClientAccess.evernightMinecraft.player);
                Float range = this.rangeSetting.getValue();
                return distance <= range;
            }
        }
        return false;
    }

    private boolean placeMaterial(AntiPhasePlacement placement) {
        Item materialItem = (this.materialSetting.getValue()).getItem();
        int hotbarSlot = InventoryUtils.findHotbarSlot(materialItem);
        int inventorySlot = InventoryUtils.findMenuSlot(materialItem);
        if (this.useBanner) {
            Predicate<ItemStack> bannerPredicate = stack -> {
                ItemStack bannerStack = stack;
                return bannerStack.getItem() instanceof BannerItem;
            };
            hotbarSlot = InventoryUtils.findHotbarSlot(bannerPredicate);
            Predicate<ItemStack> inventoryBannerPredicate = stack -> {
                ItemStack bannerStack = stack;
                return bannerStack.getItem() instanceof BannerItem;
            };
            inventorySlot = InventoryUtils.findInventorySlot(inventoryBannerPredicate, true);
        }
        int previousSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        if (hotbarSlot == -1 && inventorySlot == -1) {
            return false;
        }
        if (hotbarSlot != -1) {
            InventoryUtils.selectHotbarSlot(hotbarSlot);
            this.pendingSwap = new AntiPhaseModule.PendingSwap(previousSlot, false);
        } else {
            InventoryUtils.swapIntoSelectedSlot(inventorySlot);
            this.pendingSwap = new AntiPhaseModule.PendingSwap(inventorySlot, true);
        }
        BlockHitResult hit = new BlockHitResult(
                placement.getHitPosition(), placement.direction(), placement.supportPosition(), false);
        PlayerStateTracker interactionScheduler = ClientContext.playerStateTracker;
        Runnable interaction = () -> {
            InteractionHand interactionHand = InteractionHand.MAIN_HAND;
            PacketSender.sendUseItemOn(interactionHand, hit);
            SwingAction swingKind = SwingAction.INTERACT;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            ClientContext.swingManager.swingPacket(swingHand, swingKind);
        };
        interactionScheduler.runSneaking(interaction);
        return true;
    }

    protected boolean hasItemFrame(BlockPos position) {
        AABB boundsSnapshot = new AABB(position);
        AABB checkedBounds = boundsSnapshot.expandTowards(0.0, 0.1D, 0.0);
        Iterable<Entity> renderedEntities = ClientAccess.evernightMinecraft.level.entitiesForRendering();
        for (Entity entity : renderedEntities) {
            if (entity instanceof ItemFrame) {
                boundsSnapshot = entity.getBoundingBox();
                if (boundsSnapshot.intersects(checkedBounds)) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    private void onPlayerUpdate(RotationRequestEvent event) {
        ElapsedTimer placementTimerSnapshot = this.placementTimer;
        long delayMillisSnapshot = (this.delaySetting.getValue()).intValue();
        if (placementTimerSnapshot.hasElapsedAndReset(delayMillisSnapshot)) {
            if (!multiTask.shouldPauseModule(this)) {
                int placedCount = 0;
                List<AbstractClientPlayer> levelPlayers = ClientAccess.evernightMinecraft.level.players();
                for (AbstractClientPlayer target : levelPlayers) {
                    this.useBanner = this.shouldUseBanner(target);
                    if (this.isValidTarget(target)) {
                        AntiPhasePlacement placement = this.selectPlacementPlanner().findPlacement(target);
                        if (placement != null) {
                            if (this.placeMaterial(placement)) {
                                if (this.rotateSetting.getValue()) {
                                    float[] rotations = RotationManager.getRotationsToPosition(
                                            placement.getHitPosition());
                                    ClientContext.rotationManager.requestRotation(rotations, 25);
                                }
                                if (this.renderSetting.getValue()) {
                                    FeatureBoxRenderer rendererSnapshot = ClientContext.featureBoxRenderer;
                                    AABB renderBounds = placement.bounds();
                                    rendererSnapshot.addBox(this, renderBounds);
                                }
                                placedCount++;
                            }
                            Integer placementLimit = this.blocksPerTickSetting.getValue();
                            if (placedCount >= placementLimit) {
                                break;
                            }
                        }
                    }
                }
                if (this.pendingSwap != null) {
                    if (this.pendingSwap.inventorySwap) {
                        InventoryUtils.swapIntoSelectedSlot(this.pendingSwap.slot);
                    } else {
                        InventoryUtils.selectHotbarSlotForPlacement(this.pendingSwap.slot);
                    }
                    this.pendingSwap = null;
                }
            }
        }
    }

    record PendingSwap(int slot, boolean inventorySwap) {
    }
}