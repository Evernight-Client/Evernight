package evernight.client.modules.combat.HoleFill;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.player.SpeedMine.SpeedMineModule;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.SpeedMineRebreakChoice;
import evernight.client.utils.world.HoleInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PlainHoleFillPolicy implements ClientAccess {
    private static SpeedMineModule speedMineModule;

    static {
        speedMineModule = ClientContext.getModule(SpeedMineModule.class);
    }

    protected final HoleFillModule holeFillModule;

    public PlainHoleFillPolicy(HoleFillModule holeFillModule) {
        this.holeFillModule = holeFillModule;
    }

    public static void connectSpeedMineModule(ModuleRegistry registry) {
        speedMineModule = registry.getModule(SpeedMineModule.class);
    }

    public boolean shouldFill(HoleInfo hole) {
        if (speedMineModule.isToggled()) {
            Setting<SpeedMineRebreakChoice> rebreakSettingRef = speedMineModule.rebreakSetting;
            if (rebreakSettingRef.getValue() == SpeedMineRebreakChoice.INSTANT) {
                if (speedMineModule.getMiningPos() != null) {
                    BlockPos miningPos = speedMineModule.getMiningPos();
                    Vec3 miningCenter = miningPos.getCenter();
                    AABB miningBoundsSnapshot = hole.bounds();
                    if (miningBoundsSnapshot.contains(miningCenter)) {
                        return false;
                    }
                }
            }
        }
        AABB widthBounds = hole.bounds();
        if (widthBounds.getXsize() != 1.0D || hole.bounds().getZsize() != 1.0D) {
            if (!this.holeFillModule.doublesSetting.getValue()) {
                return false;
            }
        }
        Vec3 eyeSnapshot = ClientAccess.evernightMinecraft.player.getEyePosition();
        BlockPos rangePosition = hole.pos();
        Vec3 holeCenter = rangePosition.getCenter();
        double distance = eyeSnapshot.distanceTo(holeCenter);
        Float configuredRange = this.holeFillModule.rangeSetting.getValue();
        if (distance <= configuredRange.floatValue()) {
            double eyeY = ClientAccess.evernightMinecraft.player.getEyePosition().y;
            BlockPos verticalPosition = hole.pos();
            double verticalDifference = eyeY - verticalPosition.getCenter().y;
            double absoluteVerticalDifference = Math.abs(verticalDifference);
            Float configuredVerticalRange = this.holeFillModule.verticalRangeSetting.getValue();
            return absoluteVerticalDifference <= configuredVerticalRange.floatValue();
        }
        return false;
    }
}