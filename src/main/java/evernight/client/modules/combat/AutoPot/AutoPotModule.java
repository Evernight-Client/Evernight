package evernight.client.modules.combat.AutoPot;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.player.SwingInteractionManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.combat.BlockRaycastContext;
import evernight.client.utils.combat.BoundingBoxUtils;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.types.RegistrySetSetting;

public class AutoPotModule extends Module {
    private final Setting<Set<Potion>> potionsSetting;
    private final Setting<Integer> delaySetting;
    private final Setting<Integer> frequencySetting;
    private final Setting<Integer> healthSetting;
    private final Setting<Integer> totemsSetting;
    private final Setting<Boolean> overrideSetting;
    private final Setting<Boolean> autoDisableSetting;
    private final ElapsedTimer throwTimer;

    public AutoPotModule() {
        super("AutoPot", ModuleCategory.COMBAT);
        this.potionsSetting = this.add(new RegistrySetSetting<>("Potions", BuiltInRegistries.POTION));
        this.delaySetting = this.add(new NumberSetting<>("Delay", 75, 75, 500).withUnit("ms"));
        this.frequencySetting = this.add(new NumberSetting<>("Frequency", 1, 1, 3));
        this.healthSetting = this.add(new NumberSetting<>("Health", 8, 1, 36));
        this.totemsSetting = this.add(new NumberSetting<>("Totems", 1, 0, 10));
        this.overrideSetting = this.add(new BooleanSetting("Override", false));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", false));
        this.throwTimer = new ElapsedTimer();
        Setting<?> settingSnapshot = this.healthSetting;
        SettingLabelCondition healthLabelBoundary = SettingLabelCondition.MAX;
        settingSnapshot.setBoundaryLabel("None", healthLabelBoundary);
        settingSnapshot = this.totemsSetting;
        SettingLabelCondition totemsLabelBoundary = SettingLabelCondition.MAX;
        settingSnapshot.setBoundaryLabel("None", totemsLabelBoundary);
        Holder<Potion> healingPotionHolder = Potions.HEALING;
        Potion healingPotionSnapshot = healingPotionHolder.value();
        Holder<Potion> strongHealingPotionHolder = Potions.STRONG_HEALING;
        Potion strongHealingPotion = strongHealingPotionHolder.value();
        List<Potion> defaultPotions = List.of(healingPotionSnapshot, strongHealingPotion);
        settingSnapshot = this.potionsSetting;
        ((Set) settingSnapshot.getDefaultValue()).addAll(defaultPotions);
        (this.potionsSetting.getValue()).addAll(defaultPotions);
    }

    private static Potion getSplashPotion(ItemStack stack) {
        Item splashPotion = Items.SPLASH_POTION;
        if (!stack.is(splashPotion)) {
            return null;
        }
        DataComponentType<PotionContents> potionComponent = DataComponents.POTION_CONTENTS;
        PotionContents contents = stack.get(potionComponent);
        if (contents == null) {
            return null;
        }
        Optional<Holder<Potion>> potionOptional = contents.potion();
        Holder<Potion> potionHolder = potionOptional.orElse(null);
        if (potionHolder == null) {
            return null;
        }
        return potionHolder.value();
    }

    private float[] calculateThrowRotations() {
        Vec3 eyePosition = ClientAccess.evernightMinecraft.player.getEyePosition();
        BlockPos playerBlockPosition = BlockPos.containing(ClientAccess.evernightMinecraft.player.position());
        AABB throwBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(ClientAccess.evernightMinecraft.player, 4);
        float[] rotations = RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(throwBounds));
        double movementSpeed = ClientContext.movementSpeedTracker.getCurrentSpeed();
        Vec3 rayEndSnapshot;
        if (movementSpeed > 15.0D) {
            float pitchSnapshot = rotations[1];
            float yaw = rotations[0];
            Vec3 directionSnapshot = Vec3.directionFromRotation(pitchSnapshot, yaw);
            Vec3 scaledDirectionSnapshot = directionSnapshot.scale(4.0D);
            rayEndSnapshot = scaledDirectionSnapshot.add(eyePosition);
        } else {
            rayEndSnapshot = ClientAccess.evernightMinecraft.player.position().add(0.0, -0.01F, 0.0);
        }
        BlockRaycastContext.Builder raycastBuilder = new BlockRaycastContext.Builder(eyePosition, rayEndSnapshot);
        if (RaycastUtils.raycast(raycastBuilder.build()).getType() != Type.MISS) {
            float[] resultRotations = new float[2];
            resultRotations[0] = rotations[0];
            resultRotations[1] = rotations[1];
            return resultRotations;
        } else {
            AABB playerBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
            double minimumY = playerBounds.minY;
            AABB bottomFaceSnapshot = playerBounds.setMaxY(minimumY);
            AABB belowBounds = bottomFaceSnapshot.expandTowards(0.0, -1.0D, 0.0);
            double maximumY = playerBounds.maxY;
            AABB topFaceSnapshot = playerBounds.setMinY(maximumY);
            AABB aboveBounds = topFaceSnapshot.expandTowards(0.0, 1.0D, 0.0);
            Vec3 playerPosition = ClientAccess.evernightMinecraft.player.position();
            Stream<BlockPos> belowStream = BlockPos.betweenClosedStream(belowBounds);
            Stream<BlockPos> aboveStream = BlockPos.betweenClosedStream(aboveBounds);
            Optional<BlockPos> nearestBlockOptional = Stream.concat(belowStream, aboveStream)
                    .map(BlockPos::immutable).filter(WorldUtils::isPlacementOccupied)
                    .min(Comparator.comparingDouble(block -> {
                        double distanceZ = playerPosition.z;
                        double distanceY = playerPosition.y;
                        double distanceX = playerPosition.x;
                        return block.distToCenterSqr(distanceX, distanceY, distanceZ);
                    }));
            BlockPos nearestBlock = nearestBlockOptional.orElse(playerBlockPosition);
            Vec3 nearestCenter = nearestBlock.getCenter();
            return RotationManager.getRotationsToPosition(nearestCenter);
        }
    }

    private int calculateThrowCount() {
        if (!(this.potionsSetting.getValue()).stream().anyMatch(this::isHealingPotion)) {
            return 1;
        }
        int healingStacks = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = ClientAccess.evernightMinecraft.player.getInventory().getItem(slot);
            Potion potion = getSplashPotion(stack);
            if (potion != null && this.isHealingPotion(potion)) {
                healingStacks++;
            }
        }
        Integer frequency = this.frequencySetting.getValue();
        return Mth.clamp(healingStacks, 1, frequency);
    }

    public boolean hasNearbySplashPotion() {
        AABB searchBounds = ClientAccess.evernightMinecraft.player.getBoundingBox().inflate(3.0D);
        Iterator<Entity> entities = ClientAccess.evernightMinecraft.level.entitiesForRendering().iterator();
        while (entities.hasNext()) {
            Entity entity = entities.next();
            if (entity instanceof ThrownSplashPotion) {
                if (searchBounds.contains(entity.position())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyLocalItemUse(ItemStack stack, InteractionHand hand) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        LocalPlayer usePlayer = ClientAccess.evernightMinecraft.player;
        ItemStack resultStack;
        if (stack.use(level, usePlayer, hand) instanceof Success success) {
            resultStack = success.heldItemTransformedTo();
        } else {
            resultStack = ClientAccess.evernightMinecraft.player.getItemInHand(hand);
        }
        if (resultStack != stack) {
            ClientAccess.evernightMinecraft.player.setItemInHand(hand, resultStack);
        }
    }

    private boolean isEligiblePotion(ItemStack stack, Collection<Potion> allowedPotions) {
        Potion potion = getSplashPotion(stack);
        if (potion == null) {
            return false;
        }
        if (potion.getEffects().isEmpty()) {
            return false;
        }
        boolean hasApplicableEffect = false;
        Iterator<MobEffectInstance> effects = potion.getEffects().iterator();
        while (effects.hasNext()) {
            MobEffectInstance effect = effects.next();
            LocalPlayer effectPlayer = ClientAccess.evernightMinecraft.player;
            Holder effectType = effect.getEffect();
            if (effectPlayer.hasEffect(effectType)) {
                if (!this.overrideSetting.getValue()) {
                    continue;
                }
            }
            hasApplicableEffect = true;
        }
        return allowedPotions.contains(potion) && hasApplicableEffect;
    }

    @EventHandler
    private void onPlayerUpdate(RotationRequestEvent event) {
        if (!this.healthSetting.isAtMaximum()) {
            float health = EntityUtils.getPlayerHealthWithAbsorption();
            Integer healthLimit = this.healthSetting.getValue();
            if (health > healthLimit.intValue()) {
                return;
            }
        }
        if (!this.totemsSetting.isAtMaximum()) {
            Predicate<ItemStack> totemPredicate = stack -> {
                Item totem = Items.TOTEM_OF_UNDYING;
                return stack.is(totem);
            };
            int totemCount = InventoryUtils.countItems(totemPredicate);
            Integer totemsLimit = this.totemsSetting.getValue();
            if (totemCount > totemsLimit) {
                return;
            }
        }
        ElapsedTimer timerSnapshot = this.throwTimer;
        long delayMillisSnapshot = (this.delaySetting.getValue()).intValue();
        if (!timerSnapshot.hasElapsedAndReset(delayMillisSnapshot)) {
            return;
        }
        if (this.hasNearbySplashPotion()) {
            return;
        }
        AABB supportBounds = ClientAccess.evernightMinecraft.player.getBoundingBox().expandTowards(0.0, -2.0D, 0.0);
        if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
            return;
        }
        if (ClientAccess.evernightMinecraft.level.noBlockCollision(ClientAccess.evernightMinecraft.player, supportBounds)) {
            return;
        }
        int throwCount = this.calculateThrowCount();
        for (int throwIndex = 0; throwIndex < throwCount; throwIndex++) {
            Collection<Potion> allowedPotions = this.potionsSetting.getValue();
            if (!this.throwPotion(allowedPotions)) {
                break;
            }
        }
        if (this.autoDisableSetting.getValue()) {
            this.toggle();
        }
    }

    private boolean isHealingPotion(Potion potion) {
        Holder<Potion> healingPotion = Potions.HEALING;
        if (potion == healingPotion.value()) {
            return true;
        }
        Holder<Potion> strongHealingPotion = Potions.STRONG_HEALING;
        return potion == strongHealingPotion.value();
    }

    private void sendPotionUse(InteractionHand hand, float yaw, float pitch) {
        ServerboundUseItemPacket packet = new ServerboundUseItemPacket(hand,
                PacketSender.nextPredictionSequence(), yaw, pitch);
        PacketSender.markImportant(packet);
        PacketSender.send(packet);
    }

    public boolean throwPotion(Collection<Potion> allowedPotions) {
        int previousSlot = ClientAccess.evernightMinecraft.player.getInventory().getSelectedSlot();
        Predicate<ItemStack> eligiblePotion = candidateStack -> this.isEligiblePotion(candidateStack, allowedPotions);
        InventoryUtils.SwapSlot selectedItem = InventoryUtils.findSwapSlot(eligiblePotion, true);
        if (!selectedItem.isFound()) {
            return false;
        }
        float[] rotations = this.calculateThrowRotations();
        InventoryUtils.swapTo(selectedItem);
        InteractionHand packetHandSnapshot = selectedItem.hand();
        float yawSnapshot = rotations[0];
        float pitch = rotations[1];
        this.sendPotionUse(packetHandSnapshot, yawSnapshot, pitch);
        SwingInteractionManager swingManager = ClientContext.swingManager;
        InteractionHand swingHand = InteractionHand.MAIN_HAND;
        SwingAction swingKind = SwingAction.INTERACT;
        swingManager.swingPacket(swingHand, swingKind);
        ItemStack stackSnapshot = selectedItem.getStack();
        InteractionHand localHand = selectedItem.hand();
        this.applyLocalItemUse(stackSnapshot, localHand);
        InventoryUtils.swapBack(previousSlot, selectedItem);
        ClientContext.rotationManager.requestRotation(rotations, 5000);
        return true;
    }
}