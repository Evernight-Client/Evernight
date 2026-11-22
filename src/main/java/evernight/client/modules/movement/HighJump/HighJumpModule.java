package evernight.client.modules.movement.HighJump;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LivingEntityJumpFromGroundEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.math.Vec3Utils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import evernight.client.utils.world.BlockInteractionUtils;
import evernight.client.utils.world.WorldUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class HighJumpModule extends Module {
    private static AntiCheatModule antiCheatModule;
    final Setting<ModeChoice> modeSetting;
    private final Setting<Float> strengthSetting;
    private final Setting<Boolean> inMovementSetting;
    private final Setting<Boolean> infiniteSetting;
    private int remainingBoostTicks;
    private boolean shulkerJumpPending;
    private boolean shulkerBoostPaused;

    public HighJumpModule() {
        super("HighJump", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PLAIN));
        Predicate<Object> showStrength = ignored -> this.modeSetting.getValue() != ModeChoice.FENCE;
        this.strengthSetting = this.add(new NumberSetting<>("Strength", 1.0F, 0.0F, 5.0F, showStrength));
        Predicate<Object> showInMovement = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
        this.inMovementSetting = this.add(new BooleanSetting("InMovement", true, showInMovement));
        Predicate<Object> showInfinite = ignored -> this.modeSetting.getValue() == ModeChoice.SHULKER;
        this.infiniteSetting = this.add(new BooleanSetting("Infinite", false, showInfinite));
    }

    public static void bindReferencedModules(ModuleRegistry registry) {
        antiCheatModule = registry.getModule(AntiCheatModule.class);
    }

    @Override
    public String getInfo() {
        return (this.modeSetting.getValue()).getName();
    }

    private void startShulkerJump(LivingEntityJumpFromGroundEvent event) {
        BlockPos shulkerPos = this.findShulker(true);
        if (shulkerPos == null) {
            return;
        }
        if (((ShulkerBoxBlockEntity) ClientAccess.evernightMinecraft.level.getBlockEntity(shulkerPos)).getAnimationStatus() == AnimationStatus.CLOSED) {
            this.interactWithShulker(shulkerPos);
        } else {
            event.setJumpPower(1.0F);
        }
        this.shulkerBoostPaused = false;
        this.shulkerJumpPending = true;
        Float configuredStrength = this.strengthSetting.getValue();
        this.remainingBoostTicks = configuredStrength.intValue();
    }

    private boolean isShulkerInRange(BlockEntity blockEntity) {
        Vec3 playerPositionSnapshot = ClientAccess.evernightMinecraft.player.position();
        Vec3 blockCenterSnapshot = blockEntity.getBlockPos().getCenter();
        float distance = Vec3Utils.horizontalDistance(playerPositionSnapshot, blockCenterSnapshot);
        return distance <= 1.0F;
    }

    @EventHandler
    private void onJumpFromGround(LivingEntityJumpFromGroundEvent event) {
        HighJumpModule.ModeChoice choice = this.modeSetting.getValue();
        switch (choice) {
            case PLAIN:
                this.applyPlainJump(event);
                break;
            case FENCE:
                this.applyFenceJump(event);
                break;
            case SHULKER:
                this.startShulkerJump(event);
        }
    }

    @EventHandler
    private void onPlayerTick(LocalPlayerPreTickEvent event) {
        boolean openShulkerNearby = this.findShulker(false) != null;
        boolean anyShulkerNearby = this.findShulker(true) != null;
        if (!openShulkerNearby && !this.shulkerJumpPending || !anyShulkerNearby) {
            this.remainingBoostTicks = 0;
        }
        if (this.remainingBoostTicks <= 0 || !openShulkerNearby) {
            return;
        }
        if (!this.shulkerBoostPaused) {
            float upwardImpulse = 0.908F;
            if (antiCheatModule.is2b2tEnabled()) {
                upwardImpulse = 1.0F;
            } else if (this.remainingBoostTicks == 1) {
                upwardImpulse = 1.051F;
            }
            double upwardImpulseDouble = upwardImpulse;
            ClientAccess.evernightMinecraft.player.push(0.0, upwardImpulseDouble, 0.0);
            double risingVelocityY = ClientAccess.evernightMinecraft.player.getDeltaMovement().y;
            if (risingVelocityY >= 7.0D) {
                this.shulkerBoostPaused = true;
            }
        } else {
            double fallingVelocityY = ClientAccess.evernightMinecraft.player.getDeltaMovement().y;
            this.shulkerBoostPaused = fallingVelocityY > -0.2D;
        }
        this.shulkerJumpPending = false;
        if (!this.infiniteSetting.getValue()) {
            this.remainingBoostTicks--;
        }
    }

    private void applyPlainJump(LivingEntityJumpFromGroundEvent event) {
        if (!this.inMovementSetting.getValue() && MovementPhysics.isLocalPlayerMoving()) {
            return;
        }
        float jumpStrength = this.strengthSetting.getValue();
        event.setJumpPower(jumpStrength);
    }

    private BlockPos findShulker(boolean includeClosed) {
        for (Object blockEntityValue : WorldUtils.getLoadedBlockEntities()) {
            BlockEntity blockEntity = (BlockEntity) blockEntityValue;
            if (!(blockEntity instanceof ShulkerBoxBlockEntity shulker)) {
                continue;
            }
            if (!this.isShulkerInRange(shulker)) {
                continue;
            }
            if (shulker.getAnimationStatus() == AnimationStatus.CLOSED && !includeClosed) {
                continue;
            }
            if (shulker.getBlockState().getValue(ShulkerBoxBlock.FACING) == Direction.UP) {
                return blockEntity.getBlockPos();
            }
        }
        return null;
    }

    private void interactWithShulker(BlockPos position) {
        List<Direction> directions = BlockInteractionUtils.getStrictDirections(position);
        Direction direction = directions.isEmpty() ? Direction.UP : directions.getFirst();
        Vec3 hitPoint = position.getCenter().relative(direction, 0.5D);
        int interactionCount = this.infiniteSetting.getValue() ? 3 : 1;
        for (int i = 0; i < interactionCount; i++) {
            BlockHitResult hit = new BlockHitResult(hitPoint, direction, position, false);
            PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hit);
            ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT);
        }
    }

    private void applyFenceJump(LivingEntityJumpFromGroundEvent event) {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        AABB fenceBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        boolean intersectsFence = (level.getBlockStates(fenceBounds)).anyMatch(state -> state.getBlock() instanceof FenceBlock);
        if (intersectsFence) {
            event.setJumpPower(0.6F);
        }
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        FENCE("Fence"),
        SHULKER("Shulker");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }
}