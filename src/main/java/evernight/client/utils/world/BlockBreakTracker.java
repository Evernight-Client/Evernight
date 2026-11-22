package evernight.client.utils.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.network.ConnectScreenConnectEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.world.ServerStateManager;
import evernight.client.managers.world.WorldStateManager;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.SpeedMineRebreakChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.inventory.EnchantmentUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class BlockBreakTracker implements ClientAccess {
    private static SpeedMineModule speedMine;
    private static ItemStack miningPickaxe;

    static {
        speedMine = ClientContext.getModule(SpeedMineModule.class);
    }

    private final List<BlockBreakEntry> entries = Collections.synchronizedList(new ArrayList<>());

    public BlockBreakTracker() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    private static ItemStack getMiningPickaxe() {
        if (miningPickaxe == null) {
            miningPickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
            ItemStack pickaxe = miningPickaxe;
            ResourceKey efficiency = Enchantments.EFFICIENCY;
            Holder enchantment = EnchantmentUtils.getHolder(efficiency);
            pickaxe.enchant(enchantment, 5);
        }
        return miningPickaxe;
    }

    public static void bindModules(ModuleRegistry modules) {
        speedMine = modules.getModule(SpeedMineModule.class);
    }

    public List<BlockBreakEntry> getEntries() {
        return this.entries;
    }

    private void onBlockRemoved(BlockPos position) {
        ClientAccess.evernightMinecraft.execute(() -> {
            Iterator<BlockBreakEntry> iterator = this.entries.iterator();
            while (iterator.hasNext()) {
                BlockBreakEntry entry = iterator.next();
                if (position.equals(entry.getPosition())) {
                    Setting<SpeedMineRebreakChoice> rebreakSetting = speedMine.rebreakSetting;
                    if (rebreakSetting.getValue() != SpeedMineRebreakChoice.INSTANT) {
                        entry.setProgress(0.0F);
                        entry.setAirTicks(0);
                    }
                    entry.markBroken();
                    ElapsedTimer progressTimer = entry.getProgressTimer();
                    progressTimer.reset();
                }
            }
        });
    }

    @EventHandler
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.entries.removeIf(BlockBreakEntry::isExpired);
        Iterator<BlockBreakEntry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            BlockBreakEntry entry = iterator.next();
            ClientLevel levelSnapshot = ClientAccess.evernightMinecraft.level;
            BlockPos statePosition = entry.getPosition();
            BlockState blockState = levelSnapshot.getBlockState(statePosition);
            ItemStack pickaxeSnapshot = getMiningPickaxe();
            float baseProgress = (float) WorldUtils.getBreakProgress(pickaxeSnapshot, blockState, true);
            ServerStateManager serverStateSnapshot = ClientContext.serverStateManager;
            float progressDelta = baseProgress * serverStateSnapshot.getTickRateScale();
            SpeedMineModule speedMineSnapshot = speedMine;
            if (speedMineSnapshot.isToggled() && !entry.isExtraBreak()) {
                Setting<Float> damageSetting = speedMine.damageSetting;
                Float damageScale = damageSetting.getValue();
                progressDelta /= damageScale;
            }
            BlockPos miningPositionSnapshot = entry.getPosition();
            if (WorldUtils.canMineBlock(miningPositionSnapshot)) {
                int airTicksSnapshot = entry.getAirTicks();
                progressDelta *= Math.max(airTicksSnapshot, 1);
                entry.setAirTicks(0);
            } else {
                progressDelta = 0.0F;
                int nextAirTicks = entry.getAirTicks() + 1;
                entry.setAirTicks(nextAirTicks);
                entry.resetInactivityTimer();
            }
            float previousProgress = entry.getProgress();
            float nextProgress = previousProgress + progressDelta;
            entry.setProgress(nextProgress);
            if (Math.min(nextProgress, 1.0D) > previousProgress) {
                ElapsedTimer progressTimerSnapshot = entry.getProgressTimer();
                progressTimerSnapshot.reset();
            }
            if (!(nextProgress >= 0.8D)) {
                continue;
            }
            if (entry.isBroken()) {
                continue;
            }
            ElapsedTimer rollbackTimerSnapshot = entry.getProgressTimer();
            if (rollbackTimerSnapshot.hasElapsed(150L)) {
                continue;
            }
            WorldStateManager worldStateSnapshot = ClientContext.worldStateManager;
            BlockPos rollbackPositionSnapshot = entry.getPosition();
            worldStateSnapshot.saveBlockForRollback(rollbackPositionSnapshot);
        }
    }

    private void trackBlockBreaking(BlockPos position, int entityId) {
        boolean alreadyTracked = false;
        boolean hasExtraBreak = false;
        Iterator<BlockBreakEntry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            BlockBreakEntry entry = iterator.next();
            if (entry.getEntityId() == entityId) {
                if (entry.getPosition().equals(position)) {
                    alreadyTracked = true;
                }
                if (entry.isExtraBreak()) {
                    hasExtraBreak = true;
                }
            }
        }
        if (alreadyTracked) {
            return;
        }
        boolean extraBreak = false;
        if (!hasExtraBreak) {
            Setting<Boolean> extraBreakSetting = speedMine.extraBreakSetting;
            Boolean extraBreakEnabled = extraBreakSetting.getValue();
            extraBreak = extraBreakEnabled;
        }
        this.entries.removeIf(existingEntry -> {
            if (existingEntry.isExtraBreak()) {
                return false;
            }
            return existingEntry.getPosition().equals(position) || existingEntry.getEntityId() == entityId;
        });
        if (WorldUtils.canMineBlock(position)) {
            this.entries.add(new BlockBreakEntry(entityId, position, extraBreak));
        }
    }

    @EventHandler
    private void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
        this.entries.clear();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundBlockDestructionPacket destructionPacket) {
            ClientAccess.evernightMinecraft.execute(() -> {
                if (destructionPacket.getProgress() >= 0) {
                    int breakStage = destructionPacket.getProgress();
                    if (breakStage <= 10) {
                        BlockPos positionSnapshot = destructionPacket.getPos();
                        int breakerId = destructionPacket.getId();
                        this.trackBlockBreaking(positionSnapshot, breakerId);
                    }
                }
            });
        }
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket updatePacket) {
            BlockState updatedState = updatePacket.getBlockState();
            if (updatedState.isAir()) {
                BlockPos removedPosition = updatePacket.getPos();
                this.onBlockRemoved(removedPosition);
            }
        }
        if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            sectionPacket.runUpdates((position, updatedState) -> {
                if (updatedState.isAir()) {
                    this.onBlockRemoved(position);
                }
            });
        }
    }

    public float getBreakProgress(BlockPos position) {
        if (!WorldUtils.canMineBlock(position)) {
            return 0.0F;
        }
        return this.entries.stream().filter(entry -> {
            return entry.getPosition().equals(position);
        }).map(BlockBreakEntry::getProgress).findAny().orElse(0.0F);
    }
}