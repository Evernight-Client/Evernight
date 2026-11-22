package evernight.client.modules.render.Xray;

import evernight.client.ClientAccess;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.GameOptionsOverrides;
import evernight.client.render.shader.IrisShaderBridge;
import evernight.client.settings.options.ListFilterMode;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;

public class XrayModule extends Module {
    private static XrayModule instance;
    public final Setting<Integer> alphaSetting;
    private final Setting<Set<Block>> blocksSetting;
    private final Setting<ListFilterMode> selectionSetting;
    private final Setting<Boolean> bypassSetting;
    private double previousGamma;

    public XrayModule() {
        super(
                "Xray",
                ModuleCategory.RENDER);
        this.blocksSetting = this.add(new RegistrySetSetting<>("Blocks", BuiltInRegistries.BLOCK, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS));
        this.selectionSetting = this.add(new EnumSetting<>("Selection", ListFilterMode.WHITELIST));
        this.alphaSetting = this.add(new NumberSetting<>("Alpha", 0, 0, 100).withUnit("%"));
        this.bypassSetting = this.add(new BooleanSetting("Bypass", false));
        instance = this;
        this.selectionSetting.setChangeCallback(this::reloadRendererIfEnabled);
        this.blocksSetting.setChangeCallback(this::reloadRendererIfEnabled);
        this.bypassSetting.setChangeCallback(this::reloadRendererIfEnabled);
        this.alphaSetting.setChangeCallback(this::reloadRendererIfEnabled);
    }

    public static XrayModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        OptionInstance<Double> previousGammaOption = ClientAccess.evernightMinecraft.options.gamma();
        this.previousGamma = previousGammaOption.get();
        OptionInstance<Double> gammaOption = ClientAccess.evernightMinecraft.options.gamma();
        GameOptionsOverrides.forceSetOptionValue(gammaOption, 1000.0D);
    }

    @Override
    public void onToggle() {
        WorldUtils.reloadRenderer();
    }

    @Override
    public void onDisable() {
        Options options = ClientAccess.evernightMinecraft.options;
        OptionInstance<Double> gammaOption = options.gamma();
        double savedGamma = this.previousGamma;
        GameOptionsOverrides.forceSetOptionValue(gammaOption, savedGamma);
    }

    private void reloadRendererIfEnabled() {
        if (this.isToggled()) {
            WorldUtils.reloadRenderer();
        }
    }

    public boolean shouldRenderBlock(BlockPos position, Block block) {
        if (block == null || position == null || this.isWorldUnavailable()) {
            return false;
        }
        ListFilterMode selectionMode = this.selectionSetting.getValue();
        if (selectionMode.isAny()) {
            return false;
        }
        ListFilterMode mode = this.selectionSetting.getValue();
        Setting<Set<Block>> blocks = this.blocksSetting;
        if (!ListFilterMode.matches(mode, block, blocks)) {
            return false;
        }
        Setting<Boolean> bypass = this.bypassSetting;
        if (!(bypass.getValue())) {
            return true;
        }
        return this.hasNonOccludingNeighbor(position);
    }

    public boolean shouldHideUnselectedBlocks() {
        Setting<Integer> alpha = this.alphaSetting;
        if (!alpha.isAtMaximum()) {
            if (IrisShaderBridge.isHandRendererAvailable() && IrisShaderBridge.isShaderPackPresent()) {
                return true;
            }
        }
        return (this.alphaSetting.getValue()) == 0;
    }

    private boolean hasNonOccludingNeighbor(BlockPos position) {
        for (Direction direction : Direction.values()) {
            ClientLevel level = ClientAccess.evernightMinecraft.level;
            BlockPos neighborPosition = position.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPosition);
            if (!neighborState.canOcclude()) {
                return true;
            }
        }
        return false;
    }
}