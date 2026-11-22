package evernight.client.modules.combat.FeetPlace;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.player.InventoryStateManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FeetPlaceFireworkSupport implements ClientAccess {
    private final Map pendingFireworks = new HashMap();
    private final Map itemSpawnTimes = new HashMap();

    void onEnable() {
        this.pendingFireworks.clear();
    }

    private boolean hasHorizontalNeighbor(BlockPos position, Iterable positions) {
        Iterator iterator = positions.iterator();
        while (iterator.hasNext()) {
            BlockPos neighbor = (BlockPos) iterator.next();
            if (neighbor.distManhattan(position) == 1) {
                int positionY = position.getY();
                if (positionY == neighbor.getY()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void pruneSpawnTimes(Deque times, long now) {
        Long oldestTime;
        while ((oldestTime = (Long) times.peekFirst()) != null) {
            Long oldestTimeSnapshot = oldestTime;
            long elapsedMillis = now - oldestTimeSnapshot;
            if (elapsedMillis > 500L) {
                times.pollFirst();
                continue;
            }
            break;
        }
    }

    void processFireworks(boolean airPlace, boolean raytrace) {
        if (!ClientAccess.evernightMinecraft.player.isSpectator()) {
            if (ClientAccess.evernightMinecraft.player.isAlive()) {
                if (!ClientAccess.evernightMinecraft.player.isHandsBusy()) {
                    if (!ClientAccess.evernightMinecraft.player.isSleeping()) {
                        long now = System.currentTimeMillis();
                        long historyPruneTime = now;
                        this.pruneSpawnHistory(historyPruneTime);
                        BlockPos position = this.selectFireworkPosition();
                        if (position != null) {
                            if (!BlockInteractionUtils.isOutsidePlacementBounds(position)) {
                                boolean strictDirection = BlockInteractionUtils.isStrictDirectionEnabled();
                                Direction face = BlockInteractionUtils.findSupportDirection(position, strictDirection, raytrace);
                                if (face == null && !airPlace) {
                                    return;
                                }
                                int previousSlot = InventoryUtils.getSelectedSlot();
                                Item rocketItem = Items.FIREWORK_ROCKET;
                                InventoryUtils.SwapSlot rocketSlot = InventoryUtils.findSwapSlot(rocketItem, true);
                                if (InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2)) {
                                    if (!rocketSlot.hotbar()) {
                                        return;
                                    }
                                }
                                InventoryUtils.runWithSwap(previousSlot, rocketSlot, () -> {
                                    InteractionHand hand = rocketSlot.hand();
                                    BlockInteractionUtils.sendPlacement(position, null, face, true, hand);
                                    SwingInteractionManager swingManager = ClientContext.swingManager;
                                    InteractionHand swingHand = InteractionHand.MAIN_HAND;
                                    SwingAction interactionType = SwingAction.INTERACT;
                                    swingManager.swing(swingHand, interactionType);
                                    this.pendingFireworks.put(position, new PendingFirework(
                                            now, this.getFireworkDuration(rocketSlot), position));
                                });
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private int getFireworkDuration(InventoryUtils.SwapSlot rocketSlot) {
        ItemStack rocketStack = rocketSlot.getStack();
        return ItemUtils.getFireworkDuration(rocketStack);
    }

    private BlockPos selectFireworkPosition() {
        Stream openPositionStream = PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.evernightMinecraft.player)
                .stream().filter(WorldUtils::isAir);
        Set openPositions = (Set) openPositionStream.collect(Collectors.toSet());
        Set occupiedPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.evernightMinecraft.player);
        Set fireworkPositions = EntityUtils.getFireworkPositions();
        long now = System.currentTimeMillis();
        boolean hasThreatenedPosition = false;
        Iterator iterator = occupiedPositions.iterator();
        while (iterator.hasNext()) {
            BlockPos position = (BlockPos) iterator.next();
            if (this.hasCoveredAirBlock(position)) {
                if (this.isThreatenedPosition(position)) {
                    hasThreatenedPosition = true;
                    break;
                }
            }
        }
        iterator = occupiedPositions.iterator();
        while (iterator.hasNext()) {
            BlockPos candidate = (BlockPos) iterator.next();
            if (this.hasCoveredAirBlock(candidate) && this.hasHorizontalNeighbor(candidate, openPositions)) {
                BlockPos belowPosition = candidate.below();
                if (!WorldUtils.isBedrockOrObsidian(belowPosition)) {
                    continue;
                }
                boolean threatened = this.isThreatenedPosition(candidate);
                if (!hasThreatenedPosition || threatened) {
                    FeetPlaceFireworkSupport.PendingFirework pending = (FeetPlaceFireworkSupport.PendingFirework) this.pendingFireworks.get(candidate);
                    if (pending == null) {
                        if (threatened && !fireworkPositions.contains(candidate)) {
                            return candidate;
                        }
                    } else {
                        int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
                        long retryDelay = latencyMillis + 50;
                        long sentAtMillis = pending.sentAtMillis;
                        long durationTicks = pending.durationTicks;
                        long expiryTime = sentAtMillis + durationTicks * 50L;
                        boolean ready = now >= expiryTime || !fireworkPositions.contains(candidate);
                        if (ready && now > pending.sentAtMillis + retryDelay) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    boolean isTrackedFireworkPosition(Set positions, BlockPos position) {
        int latencyMillis = ClientContext.serverStateManager.getLatencyMillis();
        long retryDelay = latencyMillis + 50;
        FeetPlaceFireworkSupport.PendingFirework pending = (FeetPlaceFireworkSupport.PendingFirework) this.pendingFireworks.get(position);
        if (pending != null) {
            if (positions.contains(position)) {
                return true;
            }
            long now = System.currentTimeMillis();
            if (now < pending.sentAtMillis() + retryDelay) {
                return true;
            }
        }
        return positions.contains(position);
    }

    void observeSpawnPacket(PacketEvent.Receive event) {
        long receivedAt = System.currentTimeMillis();
        Packet packet = event.getPacket();
        if (packet instanceof ClientboundAddEntityPacket itemPacket) {
            if (itemPacket.getType() == EntityType.ITEM) {
                double itemX = itemPacket.getX();
                double itemY = itemPacket.getY();
                double itemZ = itemPacket.getZ();
                double capturedItemY = itemY;
                BlockPos position = BlockPos.containing(itemX, capturedItemY, itemZ);
                ClientAccess.evernightMinecraft.execute(() -> {
                    Deque spawnTimes = (Deque) this.itemSpawnTimes.computeIfAbsent(
                            position, (Function<BlockPos, Deque>) unused -> new ArrayDeque());
                    spawnTimes.addLast(receivedAt);
                    this.pruneSpawnTimes(spawnTimes, receivedAt);
                });
            }
        }
    }

    private void pruneSpawnHistory(long now) {
        this.itemSpawnTimes.entrySet().removeIf((Predicate<Entry>) entry -> {
            this.pruneSpawnTimes((Deque) entry.getValue(), now);
            return ((Deque) entry.getValue()).isEmpty();
        });
    }

    private boolean hasCoveredAirBlock(BlockPos position) {
        BlockState state = ClientAccess.evernightMinecraft.level.getBlockState(position);
        if (state.canBeReplaced()) {
            ClientLevel aboveLevel = ClientAccess.evernightMinecraft.level;
            BlockPos abovePosition = position.above();
            BlockState aboveState = aboveLevel.getBlockState(abovePosition);
            return !aboveState.canBeReplaced();
        }
        return false;
    }

    private boolean isThreatenedPosition(BlockPos position) {
        long now = System.currentTimeMillis();
        Deque spawnTimes = (Deque) this.itemSpawnTimes.get(position);
        if (spawnTimes != null) {
            this.pruneSpawnTimes(spawnTimes, now);
            int recentItemCount = spawnTimes.size();
            if (recentItemCount >= 2) {
                return true;
            }
        }
        double damage = ClientContext.blockBreakTracker.getBreakProgress(position);
        return damage > 0.9D;
    }

    record PendingFirework(long sentAtMillis, int durationTicks, BlockPos position) {
    }
}