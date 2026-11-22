package evernight.client.utils.world;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.player.Nuker.NukerModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.Setting;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.inventory.ExtraBreakTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NukerBlockQueue implements ClientAccess {
    private static SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);
    private final List<BlockPos> positions = new ArrayList<>();
    private final NukerModule nukerModule;

    public NukerBlockQueue(NukerModule nukerModule) {
        this.nukerModule = nukerModule;
    }

    public static void bindModules(ModuleRegistry modules) {
        speedMineModule = modules.getModule(SpeedMineModule.class);
    }

    public void pruneInvalidPositions() {
        this.positions.removeIf(this::shouldRemovePosition);
    }

    public List<BlockPos> getPositions() {
        return this.positions;
    }

    private boolean shouldRemovePosition(BlockPos blockPos) {
        SpeedMineModule speedMineForToggle = speedMineModule;
        if (speedMineForToggle.isToggled()) {
            SpeedMineModule speedMineSnapshot = speedMineModule;
            ExtraBreakTracker pendingTaskSnapshot = speedMineSnapshot.getExtraBreakTracker();
            if (pendingTaskSnapshot.isTracked(blockPos)) {
                return true;
            }
        }
        if (!WorldUtils.canMineBlock(blockPos)) {
            return true;
        }
        Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
        AABB blockBounds = new AABB(blockPos);
        Vec3 closestPoint = BoundingBoxUtils.clampToBounds(eyePosition, blockBounds);
        double distance = eyePosition.distanceTo(closestPoint);
        Setting<Float> rangeSettingRef = this.nukerModule.rangeSetting;
        Float configuredRange = rangeSettingRef.getValue();
        return distance > configuredRange.floatValue();
    }

    public void enqueue(BlockPos blockPos) {
        if (!this.positions.contains(blockPos)) {
            this.positions.add(blockPos);
        }
    }

    public List<BlockPos> getNextPositions(boolean includeSecond) {
        if (this.positions.isEmpty()) {
            return Collections.emptyList();
        }
        if (this.positions.size() == 1) {
            return new ArrayList<>(List.of(this.positions.getFirst()));
        }
        return new ArrayList<>(includeSecond
                ? List.of(this.positions.getFirst(), this.positions.get(1))
                : Collections.singletonList(this.positions.getFirst()));
    }
}