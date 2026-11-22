package evernight.client.modules.combat.HoleFill;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.world.BlockUpdateAppliedEvent;
import evernight.client.events.core.EventHandler;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.PlacementModule;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.world.BlockStateUtils;
import evernight.client.utils.world.HoleInfo;
import evernight.mixin.minecraft.world.BlockBehaviourAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;

public class HoleFillModule extends PlacementModule {
    public final Setting<Integer> ticksSetting;
    public final Setting<Boolean> smartSetting;
    protected final Setting<Set<Block>> blocksSetting;
    protected final Setting<Float> rangeSetting;
    protected final Setting<Float> verticalRangeSetting;
    protected final Setting<Boolean> doublesSetting;
    protected final Setting<Boolean> onlySafeSetting;
    protected final Setting<Boolean> selfSetting;
    protected final Setting<Boolean> extrapolationSetting;
    protected final Setting<Float> smartRangeSetting;
    protected final Setting<Boolean> selfSafetySetting;
    protected final Setting<Boolean> ignoreNakedsSetting;
    protected final PlainHoleFillPolicy plainPolicy;
    protected final PlainHoleFillPolicy smartPolicy;
    final Setting<ModeChoice> modeSetting;
    private final Setting<Boolean> sequentialSetting;
    protected int candidateCount;

    public HoleFillModule() {
        super("HoleFill", ModuleCategory.COMBAT);
        this.blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST));
        this.sequentialSetting = this.add(new BooleanSetting("Sequential", false));
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PLAIN));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.5F).withUnit("m"));
        this.verticalRangeSetting = this.add(new NumberSetting<>("VerticalRange", 3.0F, 1.0F, 6.0F).withUnit("m"));
        this.doublesSetting = this.add(new BooleanSetting("Doubles", false));
        this.onlySafeSetting = this.add(new BooleanSetting("OnlySafe", false));
        this.selfSetting = this.add(new BooleanSetting("Self", true));
        this.extrapolationSetting = this.add(new BooleanSetting("Extrapolation", true).markVisibilityDependency());
        Predicate<Object> ticksVisibility = unused -> this.extrapolationSetting.isExpanded();
        this.ticksSetting = this.add(new NumberSetting<>("Ticks", 4, 0, 8, ticksVisibility)
                .setBoundaryLabel("Auto", SettingLabelCondition.MIN));
        Predicate<Object> smartVisibility = unused -> this.modeSetting.getValue() == ModeChoice.SMART;
        this.smartSetting = this.add(new BooleanSetting("Smart", false, smartVisibility).markAsGroup());
        this.smartRangeSetting = this.add(new NumberSetting<>("SmartRange", 1.5F, 0.5F, 4.0F).dependsOn(this.smartSetting));
        this.selfSafetySetting = this.add(new BooleanSetting("SelfSafety", false).dependsOn(this.smartSetting));
        this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false).dependsOn(this.smartSetting));
        this.plainPolicy = new PlainHoleFillPolicy(this);
        this.smartPolicy = new SmartHoleFillPolicy(this);
        this.unregister(this.placementAirPlaceSetting);
        this.unregister(this.placementChorusSetting);
        this.unregister(this.placementAttackSetting);
        this.placementAttackSetting.setValue(false);
        this.placementJumpSetting.setDefaultValue(false);
        this.selfSetting.addVisibilityPredicate(unused -> {
            Iterator<Block> blockIterator = (this.blocksSetting.getValue()).iterator();
            while (blockIterator.hasNext()) {
                Block selectedBlock = blockIterator.next();
                if (!((BlockBehaviourAccessor) selectedBlock).isCollidable()) {
                    return true;
                }
            }
            return false;
        });
    }

    private Block getStackBlock(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        } else {
            return Blocks.VOID_AIR;
        }
    }

    @Override
    protected int getPlacementRotationPriority() {
        return 150;
    }

    @Override
    protected InventoryUtils.SwapSlot findPlacementSwapSlot() {
        if ((this.blocksSetting.getValue()).isEmpty()) {
            return super.findPlacementSwapSlot();
        }
        Predicate<ItemStack> blockPredicate = stack -> {
            return this.blocksSetting.getValue().contains(this.getStackBlock(stack));
        };
        return InventoryUtils.findSwapSlot(blockPredicate, false);
    }

    @Override
    public String getInfo() {
        return String.valueOf(this.candidateCount);
    }

    @Override
    protected void runPlacementCycle() {
        if (!ClientContext.holeManager.isInHole()) {
            if (this.onlySafeSetting.getValue()) {
                return;
            }
        }
        super.runPlacementCycle();
    }

    @EventHandler
    private void onSequentialPlacement(BlockUpdateAppliedEvent event) {
        if (this.sequentialSetting.getValue()) {
            this.runPlacementCycle();
        }
    }

    @Override
    public List<BlockPos> getPlacementPositions() {
        ArrayList<BlockPos> positions = new ArrayList<>();
        Iterator<HoleInfo> holes = ClientContext.holeManager.getHoles().iterator();
        while (holes.hasNext()) {
            HoleInfo hole = holes.next();
            if (hole.trapped()) {
                continue;
            }
            if (!(this.modeSetting.getValue()).getPolicy(this).shouldFill(hole)) {
                continue;
            }
            if (!this.selfSetting.getValue() || !this.selfSetting.isVisible()) {
                BlockPos localCheckPosition = hole.pos();
                if (localCheckPosition.equals(PlayerStateUtils.getLocalRoundedBlockPosition())) {
                    continue;
                }
            }
            boolean useAbovePosition = true;
            BlockPos holeOrigin = hole.pos();
            BlockPos aboveHole = holeOrigin.above();
            for (Direction direction : Direction.values()) {
                Axis axis = direction.getAxis();
                if (!axis.isVertical()) {
                    ClientLevel sideLevelSnapshot = ClientAccess.evernightMinecraft.level;
                    BlockPos sidePos = aboveHole.relative(direction);
                    BlockState sideState = sideLevelSnapshot.getBlockState(sidePos);
                    boolean resistantSide = BlockStateUtils.isBlastResistantBreakable(sideState);
                    if (!resistantSide) {
                        ClientLevel secondSideLevelSnapshot = ClientAccess.evernightMinecraft.level;
                        BlockPos secondSidePos = aboveHole.relative(direction);
                        BlockState sideStateSnapshot = secondSideLevelSnapshot.getBlockState(secondSidePos);
                        resistantSide = BlockStateUtils.isBlastResistantUnbreakable(sideStateSnapshot);
                    }
                    BlockState aboveStateSnapshot = ClientAccess.evernightMinecraft.level.getBlockState(aboveHole);
                    if (BlockStateUtils.isBlocking(aboveStateSnapshot) || !resistantSide) {
                        useAbovePosition = false;
                        break;
                    }
                }
            }
            if (useAbovePosition) {
                positions.add(aboveHole);
            } else {
                positions.add(hole.pos());
            }
        }
        this.candidateCount = positions.size();
        positions.sort(Comparator.comparing((BlockPos sortPos) -> {
            Vec3 eyeSnapshot = ClientAccess.evernightMinecraft.player.getEyePosition();
            Vec3 blockCenter = sortPos.getCenter();
            double distance = eyeSnapshot.distanceTo(blockCenter);
            return distance;
        }));
        return positions;
    }

    enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        SMART("Smart");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }

        public PlainHoleFillPolicy getPolicy(HoleFillModule holeFillModule) {
            return this == PLAIN ? holeFillModule.plainPolicy : holeFillModule.smartPolicy;
        }
    }
}