package evernight.client.managers.player;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerTickEvent;
import evernight.client.events.bus.EventBus;
import evernight.client.modules.combat.AutoCrystal.AutoCrystalModule;
import evernight.client.modules.render.ViewModel.ViewModelModule;
import evernight.client.modules.render.ViewModel.ViewModelModule.HandChoice;
import evernight.client.modules.render.ViewModel.ViewModelModule.TypeChoice;
import evernight.client.settings.core.Setting;
import evernight.client.settings.options.AutoCrystalInstantChoice;
import evernight.client.settings.options.AutoCrystalWeaknessChoice;
import evernight.client.utils.core.ElapsedTimer;
import evernight.client.utils.entity.PlayerState;
import evernight.client.utils.entity.SwingAction;
import evernight.client.utils.inventory.InventoryUtils;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

public class CrystalInteractionManager implements ClientAccess {
    private static final ViewModelModule viewModel;
    private static final AutoCrystalModule autoCrystal;

    static {
        viewModel = ClientContext.getModule(ViewModelModule.class);
        autoCrystal = ClientContext.getModule(AutoCrystalModule.class);
    }

    private final ElapsedTimer attackCooldownTimer = new ElapsedTimer();
    private long additionalCooldownMillis;
    private int lastAttackedEntityId;

    public CrystalInteractionManager() {
        EventBus eventBus = ClientAccess.eventBus;
        eventBus.subscribe(this);
    }

    public int getMinimumCrystalAge() {
        return autoCrystal.ticksExistedSetting.getValue();
    }

    public void setLastAttackedEntityId(int entityId) {
        this.lastAttackedEntityId = entityId;
    }

    public boolean isRotationEnabled() {
        return autoCrystal.rotateSetting.getValue();
    }

    public boolean isInstantAttackEnabled() {
        return autoCrystal.instantSetting.getValue() != AutoCrystalInstantChoice.NONE;
    }

    private boolean selectWeaknessWeapon() {
        ItemStack heldStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
        DataComponentType<?> toolComponent = DataComponents.TOOL;
        boolean holdingTool = heldStack.has(toolComponent);
        if (autoCrystal.weaknessSetting.getValue() == AutoCrystalWeaknessChoice.NONE) {
            return false;
        }
        LocalPlayer weaknessPlayer = ClientAccess.evernightMinecraft.player;
        Holder<MobEffect> weaknessEffect = MobEffects.WEAKNESS;
        if (!weaknessPlayer.hasEffect(weaknessEffect) || holdingTool) {
            return false;
        }
        Predicate<ItemStack> weaponPredicate = candidateStack -> {
            DataComponentType<?> weaponComponent = DataComponents.WEAPON;
            return candidateStack.has(weaponComponent);
        };
        int weaponSlot = InventoryUtils.findHotbarSlot(weaponPredicate);
        if (weaponSlot == -1) {
            return false;
        }
        if (autoCrystal.weaknessSetting.getValue() == AutoCrystalWeaknessChoice.SILENT) {
            autoCrystal.ignoreNextSlotChange();
        }
        InventoryUtils.selectHotbarSlotForPlacement(weaponSlot);
        return true;
    }

    public int getAttackDelayMillis() {
        return autoCrystal.breakDelaySetting.getValue();
    }

    private void scheduleClientAttack(int entityId) {
        if (!RotationManager.isMovementSyncEnabled()) {
            return;
        }
        if (RotationManager.is2b2tMode()) {
            return;
        }
        var eventLoop = ClientAccess.evernightMinecraft;
        Runnable attackTask = () -> {
            Entity targetEntity = ClientAccess.evernightMinecraft.level.getEntity(entityId);
            if (targetEntity != null) {
                ClientAccess.evernightMinecraft.player.attack(targetEntity);
            }
        };
        eventLoop.execute(attackTask);
    }

    private void resetAttackCooldown(InteractionHand hand) {
        ((PlayerState) ClientAccess.evernightMinecraft.player).evernight$resetLastAttackedServer();
        ElapsedTimer cooldownTimerRef = this.attackCooldownTimer;
        long elapsedThreshold = 200L + this.additionalCooldownMillis;
        if (!cooldownTimerRef.hasElapsedAndReset(elapsedThreshold) || hand != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!viewModel.isToggled()) {
            return;
        }
        Setting<HandChoice> handSetting = viewModel.swingHandSetting;
        if (handSetting.getValue() == ViewModelModule.HandChoice.OFFHAND) {
            return;
        }
        Setting<TypeChoice> typeSetting = viewModel.swingTypeSetting;
        if (typeSetting.getValue() == ViewModelModule.TypeChoice.VANILLA) {
            return;
        }
        ClientAccess.evernightMinecraft.player.resetOnlyAttackStrengthTicker();
        double randomFraction = Math.random();
        this.additionalCooldownMillis = (long) (randomFraction * 300.0D);
    }

    public boolean attackEntity(int entityId, boolean instantAttack) {
        if (!this.isInstantAttackEnabled() && instantAttack) {
            return false;
        }
        if (this.lastAttackedEntityId != -1 && RotationManager.isMovementSyncEnabled()) {
            return false;
        }
        int previousSlot = InventoryUtils.getSelectedSlot();
        Entity targetEntity = ClientAccess.evernightMinecraft.level.getEntity(entityId);
        boolean switchedForWeakness = false;
        if (targetEntity instanceof EndCrystal) {
            switchedForWeakness = this.selectWeaknessWeapon();
        }
        this.lastAttackedEntityId = entityId;
        InteractionHand hand = this.selectAttackHand(targetEntity, instantAttack);
        PacketSender.sendAttack(this.lastAttackedEntityId);
        if (!instantAttack) {
            ClientContext.swingManager.swingClientSide(hand);
        }
        SwingAction swingAction = SwingAction.HIT;
        InteractionHand packetSwingHand = InteractionHand.MAIN_HAND;
        ClientContext.swingManager.swingPacket(packetSwingHand, swingAction);
        this.resetAttackCooldown(hand);
        this.scheduleClientAttack(entityId);
        if (switchedForWeakness) {
            if (autoCrystal.weaknessSetting.getValue() == AutoCrystalWeaknessChoice.SILENT) {
                autoCrystal.ignoreNextSlotChange();
                InventoryUtils.selectHotbarSlotForPlacement(previousSlot);
            }
        }
        return true;
    }

    private InteractionHand selectAttackHand(Entity entity, boolean instantAttack) {
        if (entity instanceof EndCrystal && !instantAttack) {
            return ClientAccess.evernightMinecraft.player.getOffhandItem().getItem() == Items.END_CRYSTAL ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        } else {
            return InteractionHand.MAIN_HAND;
        }
    }

    public boolean attackEntity(int entityId) {
        return this.attackEntity(entityId, false);
    }

    @EventHandler(priority = -300)
    private void onLocalPlayerTickEvent(LocalPlayerTickEvent event) {
        this.lastAttackedEntityId = -1;
    }
}