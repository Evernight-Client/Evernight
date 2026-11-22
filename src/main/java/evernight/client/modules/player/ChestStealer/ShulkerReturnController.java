package evernight.client.modules.player.ChestStealer;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.managers.world.WorldStateManager;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ShulkerReturnController extends ChestStealerController {
    private final List<TrackedShulker> trackedShulkers = new ArrayList<>();
    private final Queue<TrackedShulker> pendingShulkers = new ArrayDeque<>();
    private boolean reopenRequested;
    private boolean handlingReturnClick;
    private boolean returnedThisTick;
    private boolean leftEnderChest;

    public ShulkerReturnController(ChestStealerModule module) {
        super(module);
    }

    private static Item getTrackedItem(Slot slot) {
        return slot.getItem().getItem();
    }

    private boolean isPlayerInventorySlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex <= (26);
    }

    public void onContainerSlotUpdate(int containerId, ItemStack stack) {
        Screen screen = ClientAccess.evernightMinecraft.screen;
        if (!InventoryUtils.isEnderChestScreen(screen)) {
            if (containerId == ClientAccess.evernightMinecraft.player.inventoryMenu.containerId) {
                for (TrackedShulker trackedShulker : this.trackedShulkers) {
                    if (trackedShulker.matchesItemAndName(stack)) {
                        this.reopenRequested = true;
                    }
                }
            }
        }
    }

    public void onInventoryMouseClick(MultiPlayerGameModeHandleInventoryMouseClickEvent event) {
        Screen screen = ClientAccess.evernightMinecraft.screen;
        if (InventoryUtils.isEnderChestScreen(screen) && !this.handlingReturnClick) {
            AbstractContainerMenu menu = ClientAccess.evernightMinecraft.player.containerMenu;
            int clickedSlotId = event.getSlotId();
            Slot slot = menu.getSlot(clickedSlotId);
            ShulkerReturnController.TrackedShulker trackedShulker = new ShulkerReturnController.TrackedShulker(
                    getTrackedItem(slot), slot.getItem().get(DataComponents.CUSTOM_NAME), event.getSlotId()
            );
            int clickedSlot = event.getSlotId();
            if (this.isPlayerInventorySlot(clickedSlot)) {
                ItemStack clickedStack = slot.getItem();
                TagKey shulkerTag = ItemTags.SHULKER_BOXES;
                if (clickedStack.is(shulkerTag)) {
                    if (event.getClickType() == ClickType.SWAP) {
                        if (event.getButton() < (9)) {
                            this.pendingShulkers.add(trackedShulker);
                        }
                    }
                    if (event.getClickType() == ClickType.QUICK_MOVE) {
                        this.pendingShulkers.add(trackedShulker);
                    }
                    if (event.getClickType() == ClickType.PICKUP) {
                        this.pendingShulkers.poll();
                    }
                }
            }
        }
    }

    private int findTrackedShulkerSlot(ShulkerReturnController.TrackedShulker trackedShulker) {
        int slotIndex = 27;
        while (true) {
            if (slotIndex > (62)) {
                return -1;
            }
            ItemStack stack = ClientAccess.evernightMinecraft.player.containerMenu.getSlot(slotIndex).getItem();
            if (trackedShulker.matchesItemAndName(stack)) {
                return slotIndex;
            }
            slotIndex++;
        }
    }

    public boolean hasReturnedThisTick() {
        return this.returnedThisTick;
    }

    public void reopenRequestedShulker() {
        if (!this.reopenRequested) {
            return;
        }
        BlockPos position = this.findReopenPosition();
        if (position != null) {
            Direction fallbackDirection = Direction.UP;
            Direction direction = BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).getFirst();
            double hitOffset = 0.5D;
            BlockHitResult hitResult = new BlockHitResult(position.getCenter().relative(direction, hitOffset), direction, position, false);
            ClientAccess.evernightMinecraft.player.closeContainer();
            InteractionHand interactionHand = InteractionHand.MAIN_HAND;
            PacketSender.sendUseItemOn(interactionHand, hitResult);
            SwingInteractionManager swingManager = ClientContext.swingManager;
            InteractionHand swingHand = InteractionHand.MAIN_HAND;
            SwingAction swingType = SwingAction.INTERACT;
            swingManager.swing(swingHand, swingType);
            RotationManager rotationManager = ClientContext.rotationManager;
            float[] rotations = RotationManager.getRotationsToBlock(position);
            rotationManager.requestRotation(rotations, 250);
        }
        this.reopenRequested = false;
    }

    private BlockPos findReopenPosition() {
        BlockPos lastInteracted = ChestStealerModule.getLastInteractedBlock(this.module);
        if (lastInteracted != null && this.isWithinReopenRange(lastInteracted)
                && ClientAccess.evernightMinecraft.level.getBlockState(lastInteracted).is(Blocks.ENDER_CHEST)) {
            return lastInteracted;
        }
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        WorldStateManager containerTracker = ClientContext.worldStateManager;
        for (BlockEntity blockEntity : containerTracker.getBlockEntities()) {
            if (!(blockEntity instanceof EnderChestBlockEntity)) {
                continue;
            }
            BlockPos candidate = blockEntity.getBlockPos();
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            Vec3 eyePosition = player.getEyePosition();
            Vec3 center = candidate.getCenter();
            double distance = eyePosition.distanceTo(center);
            if (distance <= 5.0D && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private boolean isWithinReopenRange(BlockPos position) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 eyePosition = player.getEyePosition();
        Vec3 center = position.getCenter();
        return eyePosition.distanceTo(center) <= 5.0D;
    }

    public void tick() {
        this.returnedThisTick = false;
        Screen screen = ClientAccess.evernightMinecraft.screen;
        if (!InventoryUtils.isEnderChestScreen(screen)) {
            this.leftEnderChest = true;
            while (!this.pendingShulkers.isEmpty()) {
                this.trackedShulkers.add(this.pendingShulkers.poll());
            }
        } else if (!this.trackedShulkers.isEmpty() && this.leftEnderChest) {
            if (!this.module.hasScreenOpenTimedOut()) {
                return;
            }
            if (ClientAccess.evernightMinecraft.player.containerMenu instanceof ChestMenu menu) {
                if (menu.getRowCount() == (3)) {
                    InventoryStateManager inventoryCoordinator = ClientContext.inventoryStateManager;
                    if (!InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, (byte) 1)) {
                        this.handlingReturnClick = true;
                        for (TrackedShulker selectedShulker : this.trackedShulkers) {
                            int matchingSlot = this.findTrackedShulkerSlot(selectedShulker);
                            if (matchingSlot != -1) {
                                if (matchingSlot >= (54) && matchingSlot <= (62)) {
                                    int originalSlot = selectedShulker.slotIndex();
                                    int hotbarSlot = matchingSlot - (54);
                                    InventoryUtils.swapWithHotbarSlot(originalSlot, hotbarSlot);
                                } else {
                                    int originalSlot = selectedShulker.slotIndex();
                                    InventoryUtils.swapSlotsWithPickup(matchingSlot, originalSlot);
                                }
                                this.returnedThisTick = true;
                                this.leftEnderChest = false;
                            }
                        }
                        this.handlingReturnClick = false;
                        this.trackedShulkers.clear();
                    }
                }
            }
        }
    }

    private record TrackedShulker(Item item, Component customName, int slotIndex) {
        public boolean matchesItemAndName(ItemStack stack) {
            if (stack.getItem() != this.item) {
                return false;
            }
            Object stackName = stack.get(DataComponents.CUSTOM_NAME);
            return Objects.equals(stackName, this.customName);
        }
    }
}