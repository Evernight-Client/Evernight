package evernight.client.modules.combat.Blocker;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PlacementModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.BlockerModeChoice;
import evernight.client.settings.options.BlockerTargetOffsets;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.world.BlockBreakTracker;
import evernight.client.utils.world.BlockStateUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.Packet;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

public class BlockerModule extends PlacementModule {
    private static SpeedMineModule speedMineModule;

    static {
        speedMineModule = ClientContext.getModule(SpeedMineModule.class);
    }

    private final Setting<BlockerModeChoice> modeSetting;
    private final Setting<Float> blockDamageSetting;
    private final Setting<Boolean> targetsSetting;
    private final Setting<Boolean> fishSetting;
    private final Setting<Boolean> surroundSetting;
    private final Setting<Boolean> cornersSetting;
    private final Setting<Boolean> faceSetting;
    private final Setting<Boolean> cevSetting;
    private final Setting<Boolean> extendedSetting;
    private final Map<BlockPos, Long> destroyedPositionExpiries;

    public BlockerModule() {
        super("Blocker", ModuleCategory.COMBAT);
        this.placementRotateSetting.setDefaultValue(true);
        this.placementRotateSetting.setValue(true);
        this.modeSetting = this.add(new EnumSetting<>("Mode", BlockerModeChoice.DAMAGE));
        this.blockDamageSetting = this.add(new NumberSetting<>("BlockDamage", 0.8F, 0.1F, 1.0F));
        this.targetsSetting = this.add(new BooleanSetting("Targets", false).markAsGroup());
        this.fishSetting = this.add(new BooleanSetting("Fish", false).dependsOn(this.targetsSetting));
        this.surroundSetting = this.add(new BooleanSetting("Surround", true)
                .markVisibilityDependency().dependsOn(this.targetsSetting));
        this.cornersSetting = this.add(new BooleanSetting("Corners", false).dependsOn(this.targetsSetting, this.surroundSetting));
        this.faceSetting = this.add(new BooleanSetting("Face", true).dependsOn(this.targetsSetting, this.surroundSetting));
        this.cevSetting = this.add(new BooleanSetting("Cev", true)
                .markVisibilityDependency().dependsOn(this.targetsSetting));
        this.extendedSetting = this.add(new BooleanSetting("Extended", false).dependsOn(this.targetsSetting, this.cevSetting));
        this.destroyedPositionExpiries = new HashMap<>();
        this.placementJumpSetting.setDefaultValue(false);
        this.blockDamageSetting.addVisibilityPredicate(unused -> {
            return this.modeSetting.getValue() == BlockerModeChoice.DAMAGE;
        });
        this.ignoreMissingPlacementBlocks();
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        speedMineModule = registry.getModule(SpeedMineModule.class);
    }

    @Override
    public String getInfo() {
        if (this.modeSetting.getValue() == BlockerModeChoice.DAMAGE) {
            return "%.1f".formatted(this.blockDamageSetting.getValue());
        } else {
            return (this.modeSetting.getValue()).getName();
        }
    }

    private boolean shouldProtectBelow() {
        if (!this.fishSetting.getValue()) {
            return false;
        }
        AABB playerBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        double playerY = ClientAccess.evernightMinecraft.player.getY();
        AABB feetBounds = playerBounds.setMaxY(playerY);
        double minX = feetBounds.minX;
        int minBlockX = Mth.floor(minX);
        double maxX = feetBounds.maxX;
        int maxBlockX = Mth.floor(maxX);
        double minZ = feetBounds.minZ;
        int minBlockZ = Mth.floor(minZ);
        double maxZ = feetBounds.maxZ;
        int maxBlockZ = Mth.floor(maxZ);
        int occupiedColumns = (maxBlockX - minBlockX + 1) * (maxBlockZ - minBlockZ + 1);
        return occupiedColumns == 1;
    }

    private List<BlockPos> getTargetOffsets() {
        ArrayList<BlockPos> offsets = new ArrayList<>();
        Collection<BlockPos> surroundPositions = PlayerStateUtils.getSurroundPositions(ClientAccess.evernightMinecraft.player);
        if (this.surroundSetting.getValue()) {
            offsets.addAll(surroundPositions);
        }
        if (this.cevSetting.getValue()) {
            for (BlockPos position : PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.evernightMinecraft.player)) {
                offsets.add(position.above(2));
                offsets.add(position.above(3));
            }
            if (this.extendedSetting.getValue()) {
                for (BlockPos position : surroundPositions) {
                    offsets.add(position.above());
                }
            }
        }
        return offsets;
    }

    @EventHandler
    private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
        synchronized (this.destroyedPositionExpiries) {
            Collection<Long> expiries = this.destroyedPositionExpiries.values();
            Predicate<Long> hasExpired = expiry -> System.currentTimeMillis() > expiry;
            expiries.removeIf(hasExpired);
        }
    }

    @Override
    public List<BlockPos> getPlacementPositions() {
        BlockPos playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition();
        ArrayList<BlockPos> triggeredTargets = new ArrayList<>();
        Iterator<BlockPos> offsetIterator = this.getTargetOffsets().iterator();
        while (offsetIterator.hasNext()) {
            BlockPos targetPosition = offsetIterator.next();
            if (this.isTargetTriggered(targetPosition)) {
                triggeredTargets.add(targetPosition);
            }
        }
        ArrayList<BlockPos> placements = new ArrayList<>();
        if (this.shouldProtectBelow()) {
            BlockPos belowPlayerPosition = playerBlockPosition.below();
            if (this.isTargetTriggered(belowPlayerPosition)) {
                Iterator<BlockPos> belowSurroundIterator = PlayerStateUtils.getSurroundPositions(ClientAccess.evernightMinecraft.player).iterator();
                while (belowSurroundIterator.hasNext()) {
                    BlockPos belowSurroundPosition = belowSurroundIterator.next();
                    BlockPos belowSidePosition = belowSurroundPosition.below();
                    placements.add(belowSidePosition);
                }
            }
        }
        Iterator<BlockPos> targetIterator = triggeredTargets.iterator();
        while (targetIterator.hasNext()) {
            BlockPos triggeredTarget = targetIterator.next();
            BlockerTargetOffsets surroundPattern = BlockerTargetOffsets.SURROUND;
            Iterator<Vec3i> surroundIterator = surroundPattern.getOffsets().iterator();
            while (surroundIterator.hasNext()) {
                Vec3i surroundOffset = surroundIterator.next();
                int positionWord = triggeredTarget.getY();
                Vec3 playerPositionForY = ClientAccess.evernightMinecraft.player.position();
                if (positionWord != (int) playerPositionForY.y()) {
                    break;
                }
                BlockPos localBlockPosition = BlockPos.containing(ClientAccess.evernightMinecraft.player.position());
                if (this.cornersSetting.getValue()) {
                    if (!triggeredTarget.offset(surroundOffset).equals(localBlockPosition)) {
                        placements.add(triggeredTarget.offset(surroundOffset));
                    }
                } else {
                    if (triggeredTarget.subtract(localBlockPosition).equals(surroundOffset)) {
                        placements.add(triggeredTarget.offset(surroundOffset));
                        break;
                    }
                }
            }
            double targetY = triggeredTarget.getY();
            double playerY = ClientAccess.evernightMinecraft.player.getY();
            if (targetY == playerY + 3.0D) {
                placements.add(triggeredTarget.below());
            } else {
                if (this.faceSetting.getValue()) {
                    placements.add(triggeredTarget.above());
                }
            }
        }
        return placements;
    }

    private boolean isTargetTriggered(BlockPos position) {
        switch (this.modeSetting.getValue()) {
            case DAMAGE:
                BlockBreakTracker breakManagerSnapshot = ClientContext.blockBreakTracker;
                float blockDamage = breakManagerSnapshot.getBreakProgress(position);
                Float minimumDamage = this.blockDamageSetting.getValue();
                return blockDamage >= minimumDamage;
            case INSTANT:
                return !BlockStateUtils.isBlastResistantUnbreakable(ClientAccess.evernightMinecraft.level.getBlockState(position));
            case DESTROY:
                synchronized (this.destroyedPositionExpiries) {
                    return this.destroyedPositionExpiries.containsKey(position);
                }
            default:
                throw new MatchException(null, null);
        }
    }

    @EventHandler
    private void onInboundBlockUpdates(PacketEvent.Receive event) {
        Packet packet = event.getPacket();
        if (BlockUpdateBatch.isSupportedPacket(packet)) {
            BlockUpdateBatch blockUpdates = new BlockUpdateBatch(event.getPacket());
            Iterator<BlockUpdateBatch.Update> updatesIterator = blockUpdates.iterator();
            while (updatesIterator.hasNext()) {
                BlockUpdateBatch.Update update = updatesIterator.next();
                BlockPos miningPosition = speedMineModule.getMiningPos();
                BlockState updatedStateSnapshot = update.state();
                TagKey airTag = BlockTags.AIR;
                if (!updatedStateSnapshot.is(airTag)) {
                    continue;
                }
                if (miningPosition != null) {
                    if (miningPosition.equals(update.position())) {
                        continue;
                    }
                }
                synchronized (this.destroyedPositionExpiries) {
                    Map<BlockPos, Long> expiryMap = this.destroyedPositionExpiries;
                    BlockPos updatedPosition = update.position();
                    long now = System.currentTimeMillis();
                    long expiry = now + 150L;
                    expiryMap.put(updatedPosition, expiry);
                }
            }
        }
    }
}