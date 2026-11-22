package evernight.client.settings.options;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.player.Nuker.NukerModule;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.utils.world.NukerBlockQueue;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum NukerShapeChoice implements NamedEntry {
    SPHERE("Sphere") {
        @Override
        public List collectPositions(NukerModule nukerModule) {
            LocalPlayer playerForPosition = ClientAccess.evernightMinecraft.player;
            Vec3 positionSnapshot = playerForPosition.position();
            Setting<Float> rangeSettingRef = nukerModule.rangeSetting;
            Float configuredRange = rangeSettingRef.getValue();
            float rangeSnapshot = configuredRange;
            return new ArrayList<>(WorldUtils.collectSphereBlocks(positionSnapshot, rangeSnapshot, true));
        }
    },
    TUNNEL("Tunnel") {
        @Override
        public List collectPositions(NukerModule nukerModule) {
            ArrayList positions = new ArrayList();
            Setting<Integer> heightSettingRef = nukerModule.heightSetting;
            double height;
            if (heightSettingRef.isAtMinimum()) {
                LocalPlayer playerForHeight = ClientAccess.evernightMinecraft.player;
                double playerHeight = playerForHeight.getBbHeight();
                height = Math.ceil(playerHeight);
            } else {
                Setting<Integer> configuredHeightSetting = nukerModule.heightSetting;
                Integer configuredHeight = configuredHeightSetting.getValue();
                height = configuredHeight.intValue();
            }
            int distance = 0;
            while (true) {
                Setting<Float> rangeSettingRef = nukerModule.rangeSetting;
                Float configuredRange = rangeSettingRef.getValue();
                double range = configuredRange.floatValue();
                if (!((double) distance <= Math.ceil(range))) {
                    return positions;
                }
                for (int heightOffset = 0; heightOffset < height; heightOffset++) {
                    LocalPlayer playerForPosition = ClientAccess.evernightMinecraft.player;
                    BlockPos position = playerForPosition.blockPosition();
                    position = position.above(heightOffset);
                    LocalPlayer playerForDirection = ClientAccess.evernightMinecraft.player;
                    Direction directionSnapshot = playerForDirection.getDirection();
                    position = position.relative(directionSnapshot, distance);
                    positions.add(position);
                }
                distance++;
            }
        }
    },
    QUEUE("Queue") {
        @Override
        public List collectPositions(NukerModule nukerModule) {
            NukerBlockQueue queueSnapshot = nukerModule.getBlockQueue();
            Setting<Boolean> extraBreakSettingRef = NukerShapeChoice.speedMineModule.extraBreakSetting;
            Boolean extraBreakEnabled = extraBreakSettingRef.getValue();
            boolean extraBreakFlag = extraBreakEnabled;
            return queueSnapshot.getNextPositions(extraBreakFlag);
        }
    },
    CROSSHAIR("Crosshair") {
        @Override
        public List collectPositions(NukerModule nukerModule) {
            if (ClientAccess.evernightMinecraft.hitResult instanceof BlockHitResult hitResult) {
                return new ArrayList<>(Collections.singletonList(hitResult.getBlockPos()));
            } else {
                return Collections.emptyList();
            }
        }
    };
    private static SpeedMineModule speedMineModule = ClientContext.getModule(SpeedMineModule.class);
    private final String displayName;

    NukerShapeChoice(String displayName) {
        this.displayName = displayName;
    }

    public static void bindModules(ModuleRegistry modules) {
        speedMineModule = modules.getModule(SpeedMineModule.class);
    }

    @Override
    public String getName() {
        return this.displayName;
    }

    public abstract List collectPositions(NukerModule nukerModule);
}