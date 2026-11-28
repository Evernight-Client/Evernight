package evernight.client.utils.combat;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.settings.core.NamedEntry;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.inventory.InventoryUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import evernight.client.modules.combat.AutoCrystal.AutoCrystalModule;

public enum CrystalDamagePolicy implements ClientAccess, NamedEntry {
    PLACE("Place"),
    HIT("Hit");
    private static final AutoCrystalModule autoCrystal = ClientContext.getModule(AutoCrystalModule.class);
    private final String displayName;

    CrystalDamagePolicy(String displayName) {
        this.displayName = displayName;
    }

    public double getMinimumDamage(LivingEntity target) {
        if (!autoCrystal.obsidianDropNearTarget && !autoCrystal.quiverThreatActive) {
            if (autoCrystal.isFacePlaceEnabled()) {
                float health = EntityUtils.getHealthWithAbsorption(target);
                Float healthLimit = autoCrystal.facePlaceHealthSetting.getValue();
                if (health <= healthLimit) {
                    return 0.6;
                }
                if (target instanceof Player player) {
                    float armor = InventoryUtils.getLowestArmorDurabilityPercent(player);
                    Integer armorLimit = autoCrystal.facePlaceArmorThresholdSetting.getValue();
                    if (armor <= armorLimit.intValue()) {
                        return 0.6;
                    }
                }
                if (target.isUsingItem()) {
                    ItemStack activeStack = target.getUseItem();
                    Item shield = Items.SHIELD;
                    if (activeStack.is(shield)) {
                        if (autoCrystal.facePlaceShieldBreakerSetting.getValue()) {
                            return 0.6;
                        }
                    }
                }
            }
            BlockState stateSnapshot = ClientAccess.evernightMinecraft.level.getBlockState(target.blockPosition());
            Block portal = Blocks.NETHER_PORTAL;
            if (stateSnapshot.is(portal)) {
                return 0.6;
            }
            double placeMinimum = (autoCrystal.minPlaceDamageSetting.getValue()).floatValue();
            double hitMinimum = (autoCrystal.minBreakDamageSetting.getValue()).floatValue();
            if (autoCrystal.minPlaceDamageSetting.shouldDisplayBoundaryLabel()) {
                placeMinimum = EntityUtils.getHealthWithAbsorption(target);
            }
            if (autoCrystal.shouldIgnoreMiningBlock()) {
                double miningMinimum = autoCrystal.alwaysIgnoreMiningSetting.getValue() ? 0.6 : 5.0;
                placeMinimum = Math.min(placeMinimum, miningMinimum);
                hitMinimum = 3.0;
            }
            double selectedMinimum = this == HIT ? hitMinimum : placeMinimum;
            double targetHealth = EntityUtils.getHealthWithAbsorption(target);
            return Math.min(selectedMinimum, targetHealth);
        } else {
            return 0.1;
        }
    }

    public double getMaximumSelfDamage() {
        if (autoCrystal.isSelfInvulnerable()) {
            return 999.0;
        }
        Float selectedMaximum;
        if (this == HIT) {
            selectedMaximum = autoCrystal.maxBreakSelfDamageSetting.getValue();
        } else {
            selectedMaximum = autoCrystal.maxPlaceSelfDamageSetting.getValue();
        }
        return selectedMaximum.floatValue();
    }

    public boolean shouldRejectDamage(
            LivingEntity target, double selfDamage, double targetDamage, CrystalLethality lethality) {
        Item totem = Items.TOTEM_OF_UNDYING;
        boolean holdingTotem = ClientAccess.evernightMinecraft.player.isHolding(totem);
        if (autoCrystal.isSelfInvulnerable()) {
            selfDamage = 0.0;
        }
        if (autoCrystal.forceSuicideSetting.getValue()) {
            return false;
        }
        if (selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
            if (autoCrystal.antiSuicideSetting.getValue()) {
                if (!lethality.isKill() || !holdingTotem) {
                    return true;
                }
            }
        }
        if (this == HIT) {
            if (lethality.isIntersection()) {
                return false;
            }
        }
        if (lethality.isKill()) {
            return false;
        }
        if (this == PLACE && selfDamage > 0.0) {
            double damageRatio = targetDamage / selfDamage;
            Float minimumRatio = autoCrystal.placeDamageRatioSetting.getValue();
            if (damageRatio < minimumRatio.floatValue()) {
                return true;
            }
        }
        if (!(targetDamage >= EntityUtils.getHealthWithAbsorption(target))) {
            if (!(targetDamage >= this.getMinimumDamage(target))) {
                return true;
            }
            return !(selfDamage <= this.getMaximumSelfDamage());
        }
        return false;
    }

    @Override
    public String getName() {
        return this.displayName;
    }
}