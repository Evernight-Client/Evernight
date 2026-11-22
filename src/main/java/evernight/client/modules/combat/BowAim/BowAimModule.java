package evernight.client.modules.combat.BowAim;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.network.GameModeUseItemPacketEvent;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.combat.TargetSelector;
import evernight.client.utils.inventory.PotionUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.math.MathUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Predicate;

public class BowAimModule extends Module {
    final Setting<Boolean> targetsSetting;
    final Setting<Boolean> playersSetting;
    private final Setting<Boolean> ignoreNakedsSetting;
    private final Setting<Boolean> animalsSetting;
    private final Setting<Boolean> hostilesSetting;
    private final Setting<Boolean> neutralsSetting;
    private float[] cachedRotations;

    public BowAimModule() {
        super("BowAim", ModuleCategory.COMBAT);
        this.targetsSetting = this.add(new BooleanSetting("Targets", true).markAsGroup());
        Predicate<Object> playersVisibility = unused -> this.targetsSetting.isExpanded();
        this.playersSetting = this.add(new BooleanSetting("Players", true, playersVisibility).markVisibilityDependency());
        Predicate<Object> nakedsVisibility = unused -> this.targetsSetting.isExpanded() && this.playersSetting.isExpanded();
        this.ignoreNakedsSetting = this.add(new BooleanSetting("IgnoreNakeds", false, nakedsVisibility));
        Predicate<Object> animalsVisibility = unused -> this.targetsSetting.isExpanded();
        this.animalsSetting = this.add(new BooleanSetting("Animals", false, animalsVisibility));
        Predicate<Object> hostilesVisibility = unused -> this.targetsSetting.isExpanded();
        this.hostilesSetting = this.add(new BooleanSetting("Hostiles", true, hostilesVisibility));
        this.neutralsSetting = this.add(new BooleanSetting("Neutrals", false).dependsOn(this.targetsSetting));
    }

    private static float[] calculateBallisticRotations(Entity target, boolean predictMotion, float predictionTicks) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        Vec3 targetPosition = target.position().add(0.0D, target.getBbHeight() / 2.0F + 0.1F, 0.0D);
        Vec3 eyePosition = player.getEyePosition();
        if (predictMotion) {
            targetPosition = targetPosition.add(MathUtils.getPositionDelta(target).scale(predictionTicks));
            eyePosition = eyePosition.add(MathUtils.getPositionDelta(player).scale(predictionTicks));
        }
        Vec3 delta = targetPosition.subtract(eyePosition);
        double horizontalDistance = Math.hypot(delta.x, delta.z);
        float charge = player.isHolding(Items.CROSSBOW) ? 1.0F : player.getTicksUsingItem() / 20.0F;
        float launchPower = Math.min((charge * charge + 2.0F * charge) / 3.0F, 1.0F);
        double gravity = 0.006F;
        float powerSquared = launchPower * launchPower;
        double discriminant = launchPower * launchPower * launchPower * launchPower
                - gravity * (gravity * (horizontalDistance * horizontalDistance) + 2.0D * delta.y * powerSquared);
        if (discriminant < 0.0D) {
            return null;
        }
        float yaw = (float) (Math.atan2(delta.z, delta.x) * 180.0D / MathConstants.PI) - MathConstants.NINETY_DEGREES;
        float pitch = (float) -Math.toDegrees(Math.atan((powerSquared - Math.sqrt(discriminant)) / (gravity * horizontalDistance)));
        return Float.isFinite(yaw) && Float.isFinite(pitch) ? new float[]{yaw, pitch} : null;
    }

    private static float getAngularDistance(Entity target) {
        Vec3 pointSnapshot = ClientAccess.evernightMinecraft.player.getEyePosition();
        AABB targetBounds = target.getBoundingBox();
        pointSnapshot = BoundingBoxUtils.clampToBounds(pointSnapshot, targetBounds);
        return MathUtils.cameraRotationDifference(pointSnapshot);
    }

    private Potion findArrowPotion() {
        int slot = 0;
        while (true) {
            Inventory sizeInventory = ClientAccess.evernightMinecraft.player.getInventory();
            if (slot >= sizeInventory.getContainerSize()) {
                return PotionUtils.MISSING_POTION;
            }
            ItemStack stack = ClientAccess.evernightMinecraft.player.getInventory().getItem(slot);
            if (stack.getItem() instanceof ArrowItem) {
                return PotionUtils.getPotion(stack);
            }
            slot++;
        }
    }

    @EventHandler
    private void onUseItemPacket(GameModeUseItemPacketEvent event) {
        ServerboundUseItemPacket packet = event.getPacket();
        LocalPlayer playerSnapshot = ClientAccess.evernightMinecraft.player;
        InteractionHand packetHand = packet.getHand();
        ItemStack heldStack = playerSnapshot.getItemInHand(packetHand);
        Item crossbow = Items.CROSSBOW;
        if (heldStack.is(crossbow) && this.cachedRotations != null) {
            event.setRotations(this.cachedRotations);
        }
    }

    @EventHandler
    private void onPlayerUpdate(RotationRequestEvent event) {
        this.cachedRotations = null;
        Item mainHandItem = ClientAccess.evernightMinecraft.player.getMainHandItem().getItem();
        boolean projectileWeapon = mainHandItem instanceof ProjectileWeaponItem;
        boolean activeUse = ClientAccess.evernightMinecraft.player.isUsingItem();
        if (CrossbowItem.isCharged(ClientAccess.evernightMinecraft.player.getMainHandItem())) {
            activeUse = true;
        }
        if (!projectileWeapon || !activeUse) {
            return;
        }
        Iterator<MobEffectInstance> effects = this.findArrowPotion().getEffects().iterator();
        while (effects.hasNext()) {
            MobEffectInstance effect = effects.next();
            Holder<MobEffect> effectHolder = effect.getEffect();
            MobEffect effectType = effectHolder.value();
            if (effectType.isBeneficial() && effectHolder != MobEffects.SLOW_FALLING && effectHolder != MobEffects.JUMP_BOOST) {
                return;
            }
        }
        TargetSelector selectionBuilder = new TargetSelector();
        selectionBuilder = selectionBuilder.withPlayers(this.playersSetting.getValue());
        selectionBuilder = selectionBuilder.withNeutrals(this.neutralsSetting.getValue());
        selectionBuilder = selectionBuilder.withAnimals(this.animalsSetting.getValue());
        selectionBuilder = selectionBuilder.withHostiles(this.hostilesSetting.getValue());
        TargetSelector sortBuilder = selectionBuilder.ignoreNakeds(this.ignoreNakedsSetting.getValue());
        Comparator<Entity> angularOrder = Comparator.comparing(BowAimModule::getAngularDistance);
        selectionBuilder = sortBuilder.sortBy(angularOrder);
        Entity target = selectionBuilder.visibleRange(128.0F).select();
        if (target == null || target instanceof EnderMan) {
            return;
        }
        AABB targetBounds = target.getBoundingBox();
        Vec3 targetCenter = targetBounds.getCenter();
        if (!RotationManager.isVisible(targetBounds)) {
            return;
        }
        if (!WorldUtils.canSeeFromEyes(targetCenter)) {
            return;
        }
        double targetDistance = target.position().distanceTo(ClientAccess.evernightMinecraft.player.getEyePosition());
        float distanceLeadTicks = (float) (targetDistance / 8.0D);
        float predictionTicks = 4.0F + distanceLeadTicks;
        float[] rotations = calculateBallisticRotations(target, true, predictionTicks);
        if (rotations == null) {
            return;
        }
        if (Float.isNaN(rotations[0])) {
            return;
        }
        if (Float.isNaN(rotations[1])) {
            return;
        }
        this.cachedRotations = rotations;
        ClientContext.rotationManager.requestRotation(rotations, 5, true);
    }
}