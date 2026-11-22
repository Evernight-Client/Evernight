package evernight.mixin.minecraft.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.bootstrap.MixinModuleRefs;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.PlayerAttackEvent;
import evernight.client.events.player.PlayerIsStayingOnGroundSurfaceEvent;
import evernight.client.managers.player.FakeplayerManager;
import evernight.client.managers.player.PlayerTickExecutor;
import evernight.client.settings.options.SocialRole;
import evernight.client.utils.core.FloatAnimation;
import evernight.client.utils.entity.EntityUtils;
import evernight.client.utils.entity.PlayerState;
import evernight.client.utils.world.BlockInteractionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements PlayerState {
    @Unique
    private long lastCrawlingTime;
    @Unique
    private long lastEatingTime;
    @Unique
    private SocialRole socialRole;
    @Unique
    private boolean nextToWall;
    @Unique
    private int lastAttackedTicksServer;
    @Unique
    private FloatAnimation healthAnimation;

    protected PlayerMixin(EntityType entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    public abstract Abilities getAbilities();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void initializePlayerState(Level level, GameProfile profile, CallbackInfo callback) {
        this.healthAnimation = new FloatAnimation();
        this.healthAnimation.setValue(EntityUtils.getHealthFraction(this));
        this.evernight$setRole(ClientContext.socialRegistry.getRole(profile.name()));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void updatePlayerState(CallbackInfo callback) {
        this.healthAnimation.setTarget(EntityUtils.getHealthFraction(this), 100L);
        if (this.getBoundingBox().getYsize() < 1.0) {
            this.lastCrawlingTime = System.currentTimeMillis();
        }
        this.nextToWall = !this.level().noCollision(this.getBoundingBox().inflate(1.0E-4, 0.0, 1.0E-4));
        if (!this.isUsingItem() || this.getItemUseProgress(this.getUseItem(), this) > 1.0F) {
            this.lastEatingTime = System.currentTimeMillis();
        }
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void onIsStayingOnGroundSurface(CallbackInfoReturnable<Boolean> callback) {
        if (ClientAccess.evernightMinecraft.player != null) {
            PlayerIsStayingOnGroundSurfaceEvent event = new PlayerIsStayingOnGroundSurfaceEvent();
            ClientAccess.eventBus.post(event);
            if (event.isCancelled()) {
                callback.setReturnValue(true);
            }
        }
    }

    @ModifyExpressionValue(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;maxUpStep()F"))
    private float modifySafeWalkStepHeight(float originalHeight) {
        if (ClientAccess.evernightMinecraft.player == null) {
            return originalHeight;
        }
        if (!MixinModuleRefs.scaffold.isToggled() || !MixinModuleRefs.scaffold.safeWalkSetting.getValue()) {
            return originalHeight;
        }
        Item heldItem = ClientAccess.evernightMinecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem();
        if (!(heldItem instanceof BlockItem blockItem)) {
            return originalHeight;
        }
        VoxelShape collisionShape = blockItem.getBlock().defaultBlockState().getCollisionShape(
                ClientAccess.evernightMinecraft.level, BlockPos.ZERO);
        return collisionShape != null && !collisionShape.isEmpty() ? (float) collisionShape.bounds().getYsize() : originalHeight;
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    public void onAttackPre(Entity target, CallbackInfo callback) {
        PlayerAttackEvent event = new PlayerAttackEvent(EventStage.Pre, target);
        ClientAccess.eventBus.post(event);
        if (event.isCancelled()) {
            callback.cancel();
        }
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void onAttackPost(Entity attackedEntity, CallbackInfo callback) {
        PlayerAttackEvent event = new PlayerAttackEvent(EventStage.Post, attackedEntity);
        ClientAccess.eventBus.post(event);
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean allowAttackMovementSlowdown(Player player, Vec3 movement) {
        return !MixinModuleRefs.sprint.isToggled() || !MixinModuleRefs.sprint.keepSetting.getValue();
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private boolean allowAttackSprintReset(Player player, boolean sprinting) {
        return !MixinModuleRefs.sprint.isToggled() || !MixinModuleRefs.sprint.keepSetting.getValue();
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private boolean allowSimulatedPlayerKnockback(
            LivingEntity entity, double strength, double directionX, double directionZ) {
        return !((Object) this instanceof FakeplayerManager.SimulatedPlayer)
                || entity != ClientAccess.evernightMinecraft.player;
    }

    @Inject(method = "entityInteractionRange", at = @At("HEAD"), cancellable = true)
    private void overrideEntityInteractionRange(CallbackInfoReturnable<Double> callback) {
        if (MixinModuleRefs.reach.isToggled()) {
            callback.setReturnValue(MixinModuleRefs.reach.attackDistanceSetting.getValue().doubleValue());
        }
    }

    @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void overrideBlockInteractionRange(CallbackInfoReturnable<Double> callback) {
        if (MixinModuleRefs.reach.isToggled()) {
            callback.setReturnValue(MixinModuleRefs.reach.interactionDistanceSetting.getValue().doubleValue());
        }
    }

    @Inject(method = "isSecondaryUseActive", at = @At("HEAD"), cancellable = true)
    private void forceSecondaryUseWhilePlacing(CallbackInfoReturnable<Boolean> callback) {
        if (BlockInteractionUtils.isPlacingBlock) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "resetOnlyAttackStrengthTicker", at = @At("HEAD"))
    private void resetServerAttackTicks(CallbackInfo callback) {
        this.evernight$resetLastAttackedServer();
    }

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;attackStrengthTicker:I", ordinal = 0, opcode = 180))
    private void incrementServerAttackTicks(CallbackInfo callback) {
        if (!PlayerTickExecutor.isRunningExtraTick()) {
            this.lastAttackedTicksServer++;
        }
    }

    @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
    private float applyNoSlowWalkingSpeed(float originalSpeed) {
        if (!this.level().isClientSide()) {
            return originalSpeed;
        }
        if (!MixinModuleRefs.noSlow.isToggled() || !MixinModuleRefs.noSlow.slownessSetting.getValue()) {
            return originalSpeed;
        }
        float walkingSpeed = this.getAbilities().getWalkingSpeed();
        if (originalSpeed >= walkingSpeed) {
            return originalSpeed;
        }
        return this.isSprinting() ? walkingSpeed * 1.3000001F : walkingSpeed;
    }

    @Override
    public long evernight$getLastEatingTime() {
        return this.lastEatingTime;
    }

    @Override
    public SocialRole evernight$getRole() {
        return this.socialRole;
    }

    @Override
    public void evernight$setRole(SocialRole role) {
        this.socialRole = role;
    }

    @Override
    public float evernight$getHealthFactor() {
        return this.healthAnimation.getValue();
    }

    @Override
    public boolean evernight$isNextToWall() {
        return this.nextToWall;
    }

    @Override
    public boolean evernight$wasCrawling() {
        return System.currentTimeMillis() - this.lastCrawlingTime <= 150L;
    }

    @Override
    public int evernight$getLastAttackedTicksServer() {
        return this.lastAttackedTicksServer;
    }

    @Override
    public void evernight$resetLastAttackedServer() {
        this.lastAttackedTicksServer = 0;
    }
}
