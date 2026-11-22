package evernight.client.modules.movement.LongJump;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.RenderUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.EnchantmentUtils;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.math.Vec3Utils;
import evernight.client.utils.movement.MovementPhysics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.input.KeyboardInputTickEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class LongJumpModule extends Module {
    public final Setting<ModeChoice> modeSetting;
    public final Setting<Float> jumpSpeedSetting;
    public final Setting<Boolean> inLiquidSetting;
    public final Setting<Integer> amountSetting;
    public final Setting<Boolean> autoJumpSetting;
    public final Setting<Boolean> useTimerSetting;
    public final Setting<Boolean> autoDisableSetting;
    protected double moveSpeed;
    protected double previousHorizontalDistance;
    protected int movementStage;
    private boolean alternateBoost;
    private boolean jumpRequested;
    private int spearUses;

    public LongJumpModule() {
        super("LongJump", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.PLAIN));
        Predicate<Object> jumpSpeedVisible = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
        this.jumpSpeedSetting = this.add(new NumberSetting<>("JumpSpeed", 1.0F, 0.0F, 5.0F, jumpSpeedVisible));
        Predicate<Object> inLiquidVisible = ignored -> this.modeSetting.getValue() == ModeChoice.PLAIN;
        this.inLiquidSetting = this.add(new BooleanSetting("InLiquid", false, inLiquidVisible));
        Predicate<Object> amountVisible = ignored -> this.modeSetting.getValue() == ModeChoice.SPEAR;
        this.amountSetting = this.add(new NumberSetting<>("Amount", 2, 1, 4, amountVisible));
        Predicate<Object> autoJumpVisible = ignored -> this.modeSetting.getValue() == ModeChoice.SPEAR;
        this.autoJumpSetting = this.add(new BooleanSetting("AutoJump", true, autoJumpVisible));
        this.useTimerSetting = this.add(new BooleanSetting("UseTimer", true));
        this.autoDisableSetting = this.add(new BooleanSetting("AutoDisable", true));
        this.movementStage = 4;
    }

    private InventoryUtils.SwapSlot findLungeWeapon() {
        Predicate<ItemStack> weaponPredicate = stack -> {
            if (ItemUtils.isSpear(stack)) {
                return EnchantmentUtils.hasEnchantment(Enchantments.LUNGE, stack);
            }
            return false;
        };
        return InventoryUtils.findSwapSlot(weaponPredicate, true);
    }

    private void handleSpearLanding() {
        boolean completedJump = this.autoDisableSetting.getValue() ? this.jumpRequested : this.spearUses > 0;
        if (this.autoDisableSetting.getValue() && completedJump) {
            this.disable();
        } else {
            if (this.autoJumpSetting.getValue()) {
                ClientContext.inputOverrides.requestJump(true);
                this.jumpRequested = true;
            } else {
                this.jumpRequested = false;
            }
            this.spearUses = 0;
        }
    }

    @Override
    public void onEnable() {
        this.jumpRequested = false;
        this.spearUses = 0;
    }

    @EventHandler
    private void onSpearTick(LocalPlayerPreTickEvent event) {
        if (this.modeSetting.getValue() != LongJumpModule.ModeChoice.SPEAR) {
            return;
        }
        int selectedSlot = InventoryUtils.getSelectedSlot();
        InventoryUtils.SwapSlot weapon = this.findLungeWeapon();
        if (!weapon.isFound()) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player.onGround()) {
            this.handleSpearLanding();
            return;
        }
        if (!this.jumpRequested) {
            return;
        }
        if (ClientAccess.evernightMinecraft.player.isFallFlying()) {
            return;
        }
        if (PlayerStateUtils.isHungerLimited()) {
            return;
        }
        int usesBeforeLunge = this.spearUses;
        Integer useLimit = this.amountSetting.getValue();
        if (usesBeforeLunge >= useLimit) {
            return;
        }
        if (PlayerStateUtils.isInLiquid(ClientAccess.evernightMinecraft.player)) {
            return;
        }
        float attackStrength = ClientAccess.evernightMinecraft.player.getAttackStrengthScale(1.0F);
        if (attackStrength < 1.0F) {
            return;
        }
        this.performSpearLunge(weapon, selectedSlot);
        this.spearUses++;
        if (this.autoDisableSetting.getValue()) {
            int usesAfterLunge = this.spearUses;
            Integer completedUseLimit = this.amountSetting.getValue();
            if (usesAfterLunge >= completedUseLimit) {
                this.disable();
            }
        }
    }

    private void performSpearLunge(InventoryUtils.SwapSlot weapon, int selectedSlot) {
        ItemStack componentStack = weapon.getStack();
        PiercingWeapon piercingWeapon = ItemUtils.getPiercingWeapon(componentStack);
        if (piercingWeapon == null) {
            return;
        }
        if (weapon.hand() == InteractionHand.OFF_HAND) {
            int containerSlot = InventoryUtils.toMenuSlot(selectedSlot);
            InventoryUtils.clickSlot(containerSlot, 40, ClickType.SWAP);
        } else {
            InventoryUtils.swapTo(weapon);
        }
        ClientAccess.evernightMinecraft.gameMode.piercingAttack(piercingWeapon);
        ClientAccess.evernightMinecraft.player.swing(InteractionHand.MAIN_HAND);
        ClientAccess.evernightMinecraft.player.resetOnlyAttackStrengthTicker();
        if (weapon.hand() == InteractionHand.OFF_HAND) {
            int convertedRestoreSlot = InventoryUtils.toMenuSlot(selectedSlot);
            InventoryUtils.clickSlot(convertedRestoreSlot, 40, ClickType.SWAP);
        } else {
            InventoryUtils.swapBack(selectedSlot, weapon);
        }
    }

    @EventHandler(priority = 100)
    public void onJumpInput(KeyboardInputTickEvent event) {
        if (event.getInput().keyPresses.jump()) {
            this.jumpRequested = true;
        }
    }

    @EventHandler(priority = 100)
    private void measureHorizontalDistance(LocalPlayerSendPositionEvent event) {
        if (event.getStage() != EventStage.Post) {
            if (this.modeSetting.getValue() == LongJumpModule.ModeChoice.PLAIN) {
                if (!MovementPhysics.isLocalPlayerMoving()) {
                    this.movementStage = 4;
                }
                double deltaX = ClientAccess.evernightMinecraft.player.getX()
                        - ClientAccess.evernightMinecraft.player.xo;
                double deltaZ = ClientAccess.evernightMinecraft.player.getZ()
                        - ClientAccess.evernightMinecraft.player.zo;
                double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                this.previousHorizontalDistance = Math.sqrt(distanceSquared);
            }
        }
    }

    @EventHandler
    private void applyPlainMovement(LocalPlayerMoveEvent event) {
        if (this.modeSetting.getValue() != ModeChoice.PLAIN || this.collidesWithSuffocatingColumn()
                || this.isLiquidBlocked() || ClientAccess.evernightMinecraft.player.isFallFlying()
                || ClientAccess.evernightMinecraft.player.isSpectator()) {
            return;
        }
        double motionY = event.getY();
        if (this.useTimerSetting.getValue()) {
            if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L)) {
                ClientContext.tickRateController.requestMultiplier(this, 1.0888F);
            } else {
                ClientContext.tickRateController.releaseMultiplier(this);
            }
        }
        if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(250L) && this.autoDisableSetting.getValue()) {
            this.toggle();
        }
        double jumpSpeedScale = 2.0F * this.jumpSpeedSetting.getValue();
        double baseSpeed = 0.2873D * jumpSpeedScale;
        if (this.movementStage == 1 && MovementPhysics.isLocalPlayerMoving()) {
            this.moveSpeed = 1.35D * MovementPhysics.applySpeedEffects(false, baseSpeed) - 0.01D;
        } else if (this.movementStage == 2 && MovementPhysics.isLocalPlayerMoving()) {
            motionY = 0.42D + MovementPhysics.getJumpBoost();
            this.moveSpeed *= this.alternateBoost ? 1.6835D : 1.395D;
        } else if (this.movementStage == 3) {
            this.moveSpeed = this.previousHorizontalDistance
                    - 0.66F * (this.previousHorizontalDistance - MovementPhysics.applySpeedEffects(true, baseSpeed));
            this.alternateBoost = !this.alternateBoost;
        } else {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            if (player.verticalCollision
                    || !ClientAccess.evernightMinecraft.level.noCollision(player.getBoundingBox().move(0.0, player.getDeltaMovement().y, 0.0))) {
                if (this.movementStage > 0) {
                    this.movementStage = MovementPhysics.isLocalPlayerMoving() ? 1 : 0;
                }
            }
            this.moveSpeed = this.previousHorizontalDistance - this.previousHorizontalDistance / 159.0D;
        }
        this.moveSpeed = Math.max(this.moveSpeed, MovementPhysics.applySpeedEffects(false, baseSpeed));
        double motionX = 0.0;
        double motionZ = 0.0;
        if (MovementPhysics.isLocalPlayerMoving()) {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            float yaw = player.getViewYRot(RenderUtils.getPartialTick());
            double[] horizontal = MovementPhysics.getHorizontalMotion(yaw, player.input, this.moveSpeed);
            motionX = horizontal[0];
            motionZ = horizontal[1];
        }
        Vec3Utils.setComponents(event.getVelocity(), motionX, motionY, motionZ);
        if (MovementPhysics.isLocalPlayerMoving()) {
            this.movementStage++;
        }
    }

    public boolean collidesWithSuffocatingColumn() {
        AABB playerBounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
        AABB columnBox = new AABB(ClientAccess.evernightMinecraft.player.blockPosition().getX(),
                playerBounds.minY, ClientAccess.evernightMinecraft.player.blockPosition().getZ(),
                ClientAccess.evernightMinecraft.player.blockPosition().getX() + 1.0D, playerBounds.maxY,
                ClientAccess.evernightMinecraft.player.blockPosition().getZ() + 1.0D);
        double inset = 1e-07D;
        AABB collisionBox = columnBox.deflate(inset);
        return ClientAccess.evernightMinecraft.level.collidesWithSuffocatingBlock(ClientAccess.evernightMinecraft.player, collisionBox);
    }

    public boolean isLiquidBlocked() {
        if (PlayerStateUtils.isInLiquid(ClientAccess.evernightMinecraft.player)) {
            return !this.inLiquidSetting.getValue();
        }
        return false;
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        SPEAR("Spear");
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
