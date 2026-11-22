package evernight.client.modules.combat.SelfTrap;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.world.BlockInteractionUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import evernight.client.modules.combat.AutoTrap.AutoTrapModule;

public class SelfTrapModule extends AutoTrapModule {
    protected Setting<Float> enemyRangeSetting;
    private Setting<Boolean> onlyHoleSetting;
    private Setting<Boolean> onlyStandingSetting;
    private Setting<Boolean> smartSetting;

    public SelfTrapModule() {
        super("SelfTrap", ModuleCategory.COMBAT);
        this.setDisableOnRespawn(true);
        this.initializeSelfTrapSettings();
        Setting<Float> range = this.rangeSetting;
        this.unregister(range);
        this.unregister(this.ignoreNakedsSetting);
        this.unregister(this.ignoreCrawlSetting);
        this.unregister(this.onlyPhasedSetting);
        this.unregister(this.logoutsSetting);
        this.unregister(this.belowSetting);
    }

    @Override
    public String getInfo() {
        return null;
    }

    private void initializeSelfTrapSettings() {
        BooleanSetting onlyHoleEditor = new BooleanSetting("OnlyHole", false);
        this.onlyHoleSetting = this.add(onlyHoleEditor);
        BooleanSetting onlyStandingEditor = new BooleanSetting("OnlyStanding", false);
        this.onlyStandingSetting = this.add(onlyStandingEditor);
        BooleanSetting smartEditor = new BooleanSetting("Smart", false);
        Setting<Boolean> smartDependency = smartEditor.markVisibilityDependency();
        this.smartSetting = this.add(smartDependency);
        NumberSetting<Float> enemyRange = new NumberSetting<>(
                "EnemyRange",
                3.0F, 1.0F, 6.0F, unused -> this.smartSetting.isExpanded());
        NumberSetting<Float> enemyRangeWithUnit = enemyRange.withUnit("m");
        this.enemyRangeSetting = this.add(enemyRangeWithUnit);
    }

    @Override
    protected boolean shouldPlaceBelow() {
        return false;
    }

    private Entity findNearestEnemy() {
        Iterable<Entity> entities = ClientAccess.evernightMinecraft.level.entitiesForRendering();
        Spliterator<Entity> entitySpliterator = entities.spliterator();
        Stream<Entity> candidateStream = StreamSupport.stream(entitySpliterator, false).filter(candidate -> {
            if (candidate == ClientAccess.evernightMinecraft.player) {
                return false;
            }
            float candidateDistance = ClientAccess.evernightMinecraft.player.distanceTo(candidate);
            if (!(candidateDistance <= this.enemyRangeSetting.getValue() && candidate instanceof Player)) {
                return false;
            }
            SocialRegistry friends = ClientContext.socialRegistry;
            Component candidateName = candidate.getName();
            String name = candidateName.getString();
            return !friends.isFriendName(name) && candidate.isAlive();
        });
        LocalPlayer distancePlayer = ClientAccess.evernightMinecraft.player;
        Comparator<Entity> nearestFirst = Comparator.comparing(distancePlayer::distanceToSqr);
        Optional<Entity> enemy = candidateStream.min(nearestFirst);
        return enemy.orElse(null);
    }

    private boolean shouldSkipUnsupportedPlacement(BlockPos blockPos) {
        boolean attackCheckFlag = true;
        if (!BlockInteractionUtils.canPlaceAt(blockPos, true, attackCheckFlag)) {
            return true;
        }
        Setting<Boolean> airPlaceSettingRef = this.placementAirPlaceSetting;
        if (airPlaceSettingRef.getValue()) {
            return false;
        }
        boolean strictDirectionSnapshot = BlockInteractionUtils.isStrictDirectionEnabled();
        Setting<Boolean> raytraceSettingRef = this.placementRaytraceSetting;
        boolean raytrace = raytraceSettingRef.getValue();
        return BlockInteractionUtils.findSupportDirection(blockPos, strictDirectionSnapshot, raytrace) == null;
    }

    @Override
    protected Player selectTarget() {
        return ClientAccess.evernightMinecraft.player;
    }

    @Override
    public List getPlacementPositions() {
        List placements = super.getPlacementPositions();
        placements.removeIf((java.util.function.Predicate<net.minecraft.core.BlockPos>) this::shouldSkipUnsupportedPlacement);
        if (this.onlyStandingSetting.getValue() && PlayerStateUtils.isLocalHeightBelowOne()) {
            return Collections.emptyList();
        }
        if (this.smartSetting.getValue()) {
            if (this.findNearestEnemy() == null) {
                return Collections.emptyList();
            }
        }
        if (this.onlyHoleSetting.getValue()) {
            if (!ClientContext.holeManager.isInHole()) {
                return Collections.emptyList();
            }
        }
        return placements;
    }
}