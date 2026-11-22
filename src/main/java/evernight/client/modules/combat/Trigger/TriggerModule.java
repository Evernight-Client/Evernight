package evernight.client.modules.combat.Trigger;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.MathUtils;
import evernight.mixin.minecraft.client.MinecraftAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.TimeUnit;

public class TriggerModule extends Module {
    final Setting<Boolean> targetsSetting;
    final Setting<Boolean> playersSetting;
    private final Setting<Float> rangeSetting;
    private final Setting<Boolean> onlyWeaponSetting;
    private final Setting<Double> delaySetting;
    private final Setting<Integer> missChanceSetting;
    private final Setting<Boolean> ignoreNakedsSetting;
    private final Setting<Boolean> animalsSetting;
    private final Setting<Boolean> hostilesSetting;
    private final Setting<Boolean> crystalsSetting;
    private final ElapsedTimer attackTimer;

    public TriggerModule() {
        super("Trigger", ModuleCategory.COMBAT);
        this.rangeSetting = this.add(new NumberSetting<>("Range", 4.5F, 0.0F, 5.0F).withUnit("m"));
        this.onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 1.0, 0.0, 5.0).withUnit("s"));
        this.missChanceSetting = this.add(new NumberSetting<>("MissChance", 10, 0, 50).withUnit("%"));
        this.targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency());
        String playersName = "Players";
        TriggerPlayersVisibilityPredicate playersVisibility = new TriggerPlayersVisibilityPredicate(this);
        this.playersSetting = this.add(new BooleanSetting(playersName, true, playersVisibility).markVisibilityDependency());
        String ignoreNakedsName = "IgnoreNakeds";
        TriggerIgnoreNakedsVisibilityPredicate ignoreNakedsVisibility = new TriggerIgnoreNakedsVisibilityPredicate(this);
        this.ignoreNakedsSetting = this.add(new BooleanSetting(ignoreNakedsName, false, ignoreNakedsVisibility));
        String animalsName = "Animals";
        TriggerAnimalsVisibilityPredicate animalsVisibility = new TriggerAnimalsVisibilityPredicate(this);
        this.animalsSetting = this.add(new BooleanSetting(animalsName, false, animalsVisibility));
        String hostilesName = "Hostiles";
        TriggerHostilesVisibilityPredicate hostilesVisibility = new TriggerHostilesVisibilityPredicate(this);
        this.hostilesSetting = this.add(new BooleanSetting(hostilesName, true, hostilesVisibility));
        String crystalsName = "Crystals";
        TriggerCrystalsVisibilityPredicate crystalsVisibility = new TriggerCrystalsVisibilityPredicate(this);
        this.crystalsSetting = this.add(new BooleanSetting(crystalsName, false, crystalsVisibility));
        this.attackTimer = new ElapsedTimer();
    }

    @EventHandler
    private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
        if (event.getStage() == EventStage.Post) {
            return;
        }
        if (this.isWorldUnavailable()) {
            return;
        }
        if (!(ClientAccess.evernightMinecraft.player.getMainHandItem().getItem() instanceof AxeItem)) {
            ItemStack componentStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
            DataComponentType<?> weaponComponent = DataComponents.WEAPON;
            if (!componentStack.has(weaponComponent)) {
                if (this.onlyWeaponSetting.getValue()) {
                    return;
                }
            }
        }
        if (!(ClientAccess.evernightMinecraft.hitResult instanceof EntityHitResult)) {
            return;
        }
        Entity target = ((EntityHitResult) ClientAccess.evernightMinecraft.hitResult).getEntity();
        boolean eligiblePlayer = false;
        if (target instanceof Player) {
            SocialRegistry socialRegistry = ClientContext.socialRegistry;
            Component targetName = target.getName();
            String playerName = targetName.getString();
            if (!socialRegistry.isFriendName(playerName)) {
                if (this.playersSetting.getValue()) {
                    eligiblePlayer = true;
                }
            }
        }
        if (eligiblePlayer) {
            if (this.ignoreNakedsSetting.getValue()) {
                if (!PlayerStateUtils.hasArmor(((Player) target))) {
                    eligiblePlayer = false;
                }
            }
        }
        if (!target.isAlive()) {
            return;
        }
        targetEligibility:
        if (!eligiblePlayer) {
            if (target instanceof AgeableMob) {
                if (this.animalsSetting.getValue()) {
                    break targetEligibility;
                }
            }
            if (target instanceof Enemy) {
                if (this.hostilesSetting.getValue()) {
                    break targetEligibility;
                }
            }
            if (!(target instanceof EndCrystal)) {
                return;
            }
            if (!this.crystalsSetting.getValue()) {
                return;
            }
        }
        float attackStrength = ClientAccess.evernightMinecraft.player.getAttackStrengthScale(0.5F);
        if (!(attackStrength >= 1.0F)) {
            return;
        }
        ElapsedTimer delayTimer = this.attackTimer;
        double delaySeconds = this.delaySetting.getValue();
        double randomizedDelay = delaySeconds * Math.random();
        TimeUnit delayUnit = TimeUnit.SECONDS;
        if (!delayTimer.hasElapsed(randomizedDelay, delayUnit)) {
            return;
        }
        float targetDistance = ClientAccess.evernightMinecraft.player.distanceTo(target);
        Float rangeValue = this.rangeSetting.getValue();
        if (targetDistance <= rangeValue) {
            int missThreshold = this.missChanceSetting.getValue();
            boolean miss = MathUtils.passesRandomThreshold(missThreshold);
            this.attackOrMiss(miss);
            this.attackTimer.reset();
        }
    }

    private void attackOrMiss(boolean miss) {
        if (miss) {
            LocalPlayer swingPlayer = ClientAccess.evernightMinecraft.player;
            InteractionHand hand = InteractionHand.MAIN_HAND;
            swingPlayer.swing(hand);
            ClientAccess.evernightMinecraft.player.resetOnlyAttackStrengthTicker();
        } else {
            ((MinecraftAccessor) ClientAccess.evernightMinecraft).attack();
        }
    }
}