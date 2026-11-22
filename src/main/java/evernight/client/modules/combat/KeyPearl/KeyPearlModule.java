package evernight.client.modules.combat.KeyPearl;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.movement.ElytraFly.ElytraFlyModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import evernight.client.modules.combat.Offhand.OffhandModule;

public class KeyPearlModule extends Module {
    private static ElytraFlyModule elytrafly;

    static {
        elytrafly = ClientContext.getModule(ElytraFlyModule.class);
    }

    private final Setting<Boolean> noEntityTraceSetting;
    private final Setting<Boolean> solidSetting;
    private final Setting<Boolean> disableOnElytraSetting;
    private final Setting<Boolean> disableOnRidingSetting;
    private final Setting<Boolean> awaitSetting;

    public KeyPearlModule() {
        super("KeyPearl", ModuleCategory.COMBAT);
        this.noEntityTraceSetting = this.add(new BooleanSetting("NoEntityTrace", true));
        this.solidSetting = this.add(new BooleanSetting("Solid", true));
        this.disableOnElytraSetting = this.add(new BooleanSetting("DisableOnElytra", false));
        this.disableOnRidingSetting = this.add(new BooleanSetting("DisableOnRiding", false));
        this.awaitSetting = this.add(new BooleanSetting("Await", false));
        this.setDrawn(false);
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        elytrafly = registry.getModule(ElytraFlyModule.class);
    }

    @Override
    public void onEnable() {
        if (this.isWorldUnavailable()) {
            this.disable();
        } else {
            if (!this.awaitSetting.getValue()) {
                if (PlayerStateUtils.isEnderPearlOnCooldown()) {
                    this.disable();
                    return;
                }
            }
            boolean shouldDisable = false;
            if (elytrafly.isToggled()) {
                if (this.disableOnElytraSetting.getValue()) {
                    if (!ClientAccess.evernightMinecraft.player.onGround()) {
                        shouldDisable = PlayerStateUtils.isWearingElytra(ClientAccess.evernightMinecraft.player);
                    }
                }
            }
            if (ClientAccess.evernightMinecraft.player.isPassenger()) {
                if (this.disableOnRidingSetting.getValue()) {
                    shouldDisable = true;
                }
            }
            if (this.disableOnElytraSetting.getValue()) {
                ElapsedTimer timerSnapshot = ClientContext.playerStateTracker.getFallFlyingTimer();
                if (!timerSnapshot.hasElapsed(150L)) {
                    shouldDisable = true;
                }
            }
            if (shouldDisable) {
                this.disable();
            }
        }
    }

    private boolean isObstructingBlock(BlockPos position) {
        BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(position);
        if (!state.canBeReplaced()) {
            Block cobweb = Blocks.COBWEB;
            return !state.is(cobweb);
        }
        return false;
    }

    private void swapPearlSlot(int slot, boolean inventorySwap) {
        if (inventorySwap) {
            InventoryUtils.swapIntoSelectedSlot(slot);
        } else {
            InventoryUtils.selectHotbarSlotForPlacement(slot);
        }
    }

    @EventHandler(priority = 100)
    private void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (event.getStage() == EventStage.Pre) {
            if (this.awaitSetting.getValue()) {
                if (PlayerStateUtils.isEnderPearlOnCooldown()) {
                    return;
                }
            }
            HitResult hitResult = RaycastUtils.raycastWithEntityTrace(ClientAccess.evernightMinecraft.player);
            Item pearlItemSnapshot = Items.ENDER_PEARL;
            int hotbarPearlSlot = InventoryUtils.findHotbarSlot(pearlItemSnapshot);
            Item inventoryPearlItem = Items.ENDER_PEARL;
            int inventoryPearlSlot = InventoryUtils.findMenuSlot(inventoryPearlItem);
            int previousSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
            boolean entityTraceBlocked = false;
            if (this.noEntityTraceSetting.getValue()) {
                if (hitResult instanceof EntityHitResult entityHit) {
                    Entity hitEntity = entityHit.getEntity();
                    if (!hitEntity.getBoundingBox().contains(ClientAccess.evernightMinecraft.player.getEyePosition())) {
                        entityTraceBlocked = true;
                    }
                }
            }
            if (inventoryPearlSlot != -1 && !entityTraceBlocked) {
                if (hitResult instanceof BlockHitResult blockHit) {
                    BlockPos blockPositionSnapshot = blockHit.getBlockPos();
                    if (this.isObstructingBlock(blockPositionSnapshot)) {
                        if (blockHit.getType() != Type.MISS) {
                            if (!this.solidSetting.getValue()) {
                                this.disable();
                                return;
                            }
                        }
                    }
                }
                if (!OffhandModule.isRecentSwapWindowActive()) {
                    boolean useInventorySwap = hotbarPearlSlot == -1;
                    int selectedPearlSlot = useInventorySwap ? inventoryPearlSlot : hotbarPearlSlot;
                    this.swapPearlSlot(selectedPearlSlot, useInventorySwap);
                    MultiPlayerGameMode gameMode = ClientAccess.evernightMinecraft.gameMode;
                    LocalPlayer usePlayer = ClientAccess.evernightMinecraft.player;
                    InteractionHand useHand = InteractionHand.MAIN_HAND;
                    gameMode.useItem(usePlayer, useHand);
                    SwingInteractionManager swingManager = ClientContext.swingManager;
                    InteractionHand swingHand = InteractionHand.MAIN_HAND;
                    SwingAction swingMode = SwingAction.INTERACT;
                    swingManager.swing(swingHand, swingMode);
                    int previousPearlSlot = useInventorySwap ? inventoryPearlSlot : previousSlot;
                    this.swapPearlSlot(previousPearlSlot, useInventorySwap);
                    ClientContext.moduleRegistry.getPlacementModules().stream().filter(placementModule -> {
                        return placementModule.placementPearlSetting.getValue();
                    }).forEach(Module::disable);
                    this.disable();
                }
            } else {
                this.disable();
            }
        }
    }
}