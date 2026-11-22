package evernight.client.integrations.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IElytraProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.interfaces.IGoalRenderPos;
import evernight.client.ClientAccess;
import evernight.client.NamedFeature;
import evernight.client.events.bus.EventBus;
import evernight.client.utils.chat.ChatUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import evernight.client.settings.core.Setting;

public final class BaritoneAdapter implements ClientAccess, BaritoneBridge {
    /**
     * Blocks the auto miner wants Baritone to stay away from while mining. Sculk family and spawners
     * keep the path away from warden / structure triggers, mirroring the reference implementation.
     */
    private static final List<Block> MINING_AVOID_BLOCKS = List.of(
            Blocks.SCULK_SENSOR,
            Blocks.SCULK,
            Blocks.SCULK_VEIN,
            Blocks.SCULK_CATALYST,
            Blocks.SCULK_SHRIEKER,
            Blocks.SPAWNER,
            Blocks.TRIAL_SPAWNER);
    /**
     * Block properties the builder must not validate while the auto miner runs: the ender chest (and the
     * shulker box) take their facing from the player's view, so a one block schematic whose wanted state
     * is the default state would never match what a click actually places.
     */
    private static final List<String> MINING_IGNORED_BUILD_PROPERTIES = List.of("facing");
    private final ObjectSet<NamedFeature> pauseRequests = ObjectSets.synchronize(new ObjectOpenHashSet<>());
    private final Map<Settings.Setting<?>, Object> miningSettingsBackup = new LinkedHashMap<>();

    public BaritoneAdapter() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
        IPathingControlManager controlManager = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager();
        controlManager.registerProcess(new BaritoneAdapter.PauseProcess());
        baritone.api.Settings.Setting messageTagSetting = BaritoneAPI.getSettings().useMessageTag;
        messageTagSetting.value = false;
        BaritoneAPI.getSettings().logger.value = message -> {
            MutableComponent copiedMessage = message.copy();
            List<Component> siblings = copiedMessage.getSiblings();
            siblings.addFirst(Component.literal(" "));
            siblings = copiedMessage.getSiblings();
            siblings.addFirst(ChatUtils.createPrefix());
            Gui gui = ClientAccess.evernightMinecraft.gui;
            ChatComponent chat = gui.getChat();
            GuiMessageTag tag = ChatUtils.MESSAGE_TAG;
            chat.addMessage(copiedMessage, null, tag);
        };
    }

    @Override
    public void pathElytraTo(BlockPos position) {
        IElytraProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
        process.pathTo(position);
    }

    @Override
    public void setGoal(BlockPos position) {
        GoalBlock goal = new GoalBlock(position);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(goal);
    }

    @Override
    public boolean isPathingOrCalculating() {
        IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
        return pathing.isPathing() || pathing.getInProgress().isPresent();
    }

    @Override
    public boolean isInSelection(BlockPos position) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone.getSelectionManager().getLastSelection() == null) {
            return false;
        }
        ISelection[] selections = baritone.getSelectionManager().getSelections();
        for (ISelection selection : selections) {
            BetterBlockPos minimum = selection.min();
            BetterBlockPos maximum = selection.max();
            AABB bounds = AABB.encapsulatingFullBlocks(minimum, maximum);
            Vec3 center = position.getCenter();
            if (bounds.contains(center)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseRequested() {
        return !this.pauseRequests.isEmpty();
    }

    @Override
    public BlockPos getGoalPosition() {
        Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
        if (goal instanceof IGoalRenderPos renderGoal) {
            return renderGoal.getGoalPos();
        } else if (goal instanceof GoalXZ horizontalGoal) {
            double x = horizontalGoal.getX();
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            double y = player.getY();
            double z = horizontalGoal.getZ();
            return BlockPos.containing(x, y, z);
        } else {
            return null;
        }
    }

    @Override
    public void pathTo(BlockPos position) {
        GoalBlock goal = new GoalBlock(position);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    @Override
    public void pathToXZ(int x, int z) {
        GoalXZ goal = new GoalXZ(x, z);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    @Override
    public void cancelPathing() {
        IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
        pathing.cancelEverything();
        pathing.forceCancel();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    // ---- auto mining bridge ----

    @Override
    public void requestPause(NamedFeature feature) {
        this.pauseRequests.add(feature);
    }

    @Override
    public void releasePause(NamedFeature feature) {
        this.pauseRequests.remove(feature);
    }

    @Override
    public void pathForward() {
        Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
        float playerYaw = ClientAccess.evernightMinecraft.player.getYRot();
        GoalXZ goal = GoalXZ.fromDirection(playerPosition, playerYaw, 5.0D);
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    @Override
    public void mineBlocks(Block... blocks) {
        if (blocks.length == 0) {
            return;
        }
        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(blocks);
    }

    @Override
    public void cancelMining() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getMineProcess().cancel();
        baritone.getFollowProcess().cancel();
    }

    @Override
    public boolean isMining() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive();
    }

    @Override
    public void pickupItems(Predicate<ItemStack> filter) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().pickup(filter);
    }

    @Override
    public void clearArea(BlockPos firstCorner, BlockPos secondCorner) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().clearArea(firstCorner, secondCorner);
    }

    @Override
    public void placeBlock(BlockPos position, BlockState state) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess()
                .build("evernight-autominer", new SingleBlockSchematic(state), position);
    }

    @Override
    public boolean isBuilding() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive();
    }

    @Override
    public void applyMiningProfile(boolean avoidMobs, boolean avoidBlocks,
                                   boolean autoTool, int minY, int maxY) {
        Settings settings = BaritoneAPI.getSettings();
        this.rememberSetting(settings.autoTool);
        this.rememberSetting(settings.minYLevelWhileMining);
        this.rememberSetting(settings.maxYLevelWhileMining);
        this.rememberSetting(settings.mineScanDroppedItems);
        this.rememberSetting(settings.buildIgnoreProperties);
        if (avoidMobs) {
            this.rememberSetting(settings.avoidance);
            this.rememberSetting(settings.mobAvoidanceRadius);
            this.rememberSetting(settings.mobAvoidanceCoefficient);
            this.rememberSetting(settings.mobSpawnerAvoidanceRadius);
        }
        if (avoidBlocks) {
            this.rememberSetting(settings.blocksToAvoid);
        }

        settings.autoTool.value = autoTool;
        settings.minYLevelWhileMining.value = toBaritoneMinY(minY);
        settings.maxYLevelWhileMining.value = maxY;
        // dropped ore of the target type counts as a destination, so nothing mined gets left behind
        settings.mineScanDroppedItems.value = true;
        settings.buildIgnoreProperties.value = new ArrayList<>(MINING_IGNORED_BUILD_PROPERTIES);
        if (avoidMobs) {
            settings.avoidance.value = true;
            settings.mobAvoidanceRadius.value = 12;
            settings.mobAvoidanceCoefficient.value = 5.0D;
            settings.mobSpawnerAvoidanceRadius.value = 16;
        }
        if (avoidBlocks) {
            settings.blocksToAvoid.value = new ArrayList<>(MINING_AVOID_BLOCKS);
        }
    }

    @Override
    public void setMiningYRange(int minY, int maxY) {
        Settings settings = BaritoneAPI.getSettings();
        this.rememberSetting(settings.minYLevelWhileMining);
        this.rememberSetting(settings.maxYLevelWhileMining);
        settings.minYLevelWhileMining.value = toBaritoneMinY(minY);
        settings.maxYLevelWhileMining.value = maxY;
    }

    /**
     * Baritone stores its lower mining bound relative to the bottom of the dimension while the module
     * exposes an absolute Y level, so the world floor has to be subtracted before writing it.
     */
    private int toBaritoneMinY(int minY) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        int worldMinY = level != null ? level.dimensionType().minY() : -64;
        return minY - worldMinY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restoreMiningProfile() {
        for (Map.Entry<Settings.Setting<?>, Object> entry : this.miningSettingsBackup.entrySet()) {
            Settings.Setting<Object> setting = (Settings.Setting<Object>) entry.getKey();
            setting.value = entry.getValue();
        }
        this.miningSettingsBackup.clear();
    }

    /**
     * Snapshots a setting's current value the first time it is touched, so re-applying a profile never
     * overwrites the snapshot with an already modified value.
     */
    private <T> void rememberSetting(Settings.Setting<T> setting) {
        if (!this.miningSettingsBackup.containsKey(setting)) {
            this.miningSettingsBackup.put(setting, setting.value);
        }
    }

    private class PauseProcess implements IBaritoneProcess {
        private static final String displayName;

        static {
            displayName = "Evernight pause service";
        }

        @Override
        public double priority() {
            return 999.0D;
        }

        @Override
        public boolean isActive() {
            return !BaritoneAdapter.this.pauseRequests.isEmpty();
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public String displayName0() {
            return displayName;
        }

        @Override
        public PathingCommand onTick(boolean calculationFailed, boolean safeToCancel) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
    }
}