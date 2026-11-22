package evernight.client.modules.combat.Criticals;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.LocalPlayerPreTickEvent;
import evernight.client.events.network.PacketEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.movement.WallsVelocityHandler;
import evernight.client.utils.network.InteractPacketUtils;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;
import evernight.client.modules.combat.Aura.AuraModule;

public class CriticalsModule extends Module {
    private static AuraModule auraModule;

    static {
        auraModule = ClientContext.getModule(AuraModule.class);
    }

    private final Setting<ModeChoice> modeSetting;
    private final Setting<Boolean> pauseOnMoveSetting;
    private final Setting<Boolean> onlyFullSetting;
    private final Setting<Boolean> onlyWeaponSetting;
    private boolean pendingPositionCorrection;

    public CriticalsModule() {
        super("Criticals", ModuleCategory.COMBAT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", ModeChoice.GRIM));
        this.pauseOnMoveSetting = this.add(new BooleanSetting("PauseOnMove", true));
        this.onlyFullSetting = this.add(new BooleanSetting("OnlyFull", false));
        this.onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
        this.pauseOnMoveSetting.addVisibilityPredicate(unused -> {
            Setting<ModeChoice> modeSource = this.modeSetting;
            return modeSource.getValue() != ModeChoice.GRIMV3;
        });
        this.onlyFullSetting.addVisibilityPredicate(unused -> {
            Setting<ModeChoice> modeSource = this.modeSetting;
            return modeSource.getValue() == ModeChoice.GRIMV3;
        });
    }

    public static void bindModules(ModuleRegistry registry) {
        auraModule = registry.getModule(AuraModule.class);
    }

    private boolean isCriticalWeapon(ItemStack stack) {
        Item mace = Items.MACE;
        if (stack.is(mace)) {
            return true;
        }
        return ItemUtils.isSword(stack) || ItemUtils.isAxe(stack) || stack.is(ItemTags.PICKAXES);
    }

    private float clampPitch(float pitch) {
        float lowerBound = -MathConstants.NINETY_DEGREES;
        float upperBound = MathConstants.NINETY_DEGREES;
        return Math.clamp(pitch, lowerBound, upperBound);
    }

    @Override
    public String getInfo() {
        return (this.modeSetting.getValue()).getName();
    }

    protected boolean canPerformCritical() {
        if (this.onlyWeaponSetting.getValue()) {
            ItemStack heldStack = ClientAccess.evernightMinecraft.player.getMainHandItem();
            if (!this.isCriticalWeapon(heldStack)) {
                return false;
            }
        }
        movementGate:
        {
            if (!this.pauseOnMoveSetting.getValue()) {
                if (this.modeSetting.getValue() != CriticalsModule.ModeChoice.GRIMV3) {
                    break movementGate;
                }
            }
            if (MovementPhysics.isLocalPlayerMoving()) {
                return false;
            }
        }
        if (this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIMV3) {
            if (this.onlyFullSetting.getValue()) {
                double eyeY = ClientAccess.evernightMinecraft.player.getEyeY();
                if (!WallsVelocityHandler.hasPlayerCollisionAtHeight(eyeY)) {
                    return false;
                }
            }
            if (ClientAccess.evernightMinecraft.player.onGround()) {
                return WallsVelocityHandler.hasUsableGroundCollision();
            }
            return false;
        } else {
            if (this.modeSetting.getValue() == CriticalsModule.ModeChoice.GRIM) {
                if (!ClientAccess.evernightMinecraft.player.onGround()) {
                    return true;
                }
            }
            if (ClientAccess.evernightMinecraft.player.onGround() && ClientAccess.evernightMinecraft.player.verticalCollision) {
                if (!ClientAccess.evernightMinecraft.player.isInLava()) {
                    return !ClientAccess.evernightMinecraft.player.isInWater();
                }
            }
            return false;
        }
    }

    public CriticalsModule.ModeChoice getMode() {
        return this.modeSetting.getValue();
    }

    @EventHandler
    private void onLocalPlayerPreTick(LocalPlayerPreTickEvent ignoredEvent) {
        float[] rotations = new float[2];
        LocalPlayer yawSource = ClientAccess.evernightMinecraft.player;
        rotations[0] = yawSource.getYRot();
        LocalPlayer pitchSource = ClientAccess.evernightMinecraft.player;
        rotations[1] = pitchSource.getXRot();
        if (auraModule.getTarget() != null) {
            if (auraModule.isToggled()) {
                rotations = RotationManager.getRotationsToEntity(auraModule.getTarget());
            }
        }
        if (this.pendingPositionCorrection) {
            double playerX = ClientAccess.evernightMinecraft.player.getX();
            double x = playerX + 9.999999747378752e-05D;
            double y = ClientAccess.evernightMinecraft.player.getY();
            double z = ClientAccess.evernightMinecraft.player.getZ();
            float yaw = rotations[0];
            float pitch = rotations[1];
            float pitchAdjustment = AntiCheatModule.ROTATION_JITTER_STEP;
            pitch -= pitchAdjustment * 2.0F;
            float clampedPitch = this.clampPitch(pitch);
            PacketSender.sendPositionRotation(x, y, z, yaw, clampedPitch, true);
            this.pendingPositionCorrection = false;
        }
    }

    @EventHandler
    private void onConnectionSendPacketEvent(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundInteractPacket packet) {
            if (this.shouldApplyCritical(packet)) {
                Entity target = InteractPacketUtils.getEntity(packet);
                if (target == null) {
                    return;
                }
                if (!event.isCancelled()) {
                    this.performCritical();
                }
                BlockableEventLoop executor = ClientAccess.evernightMinecraft;
                Runnable criticalEffect = () -> {
                    ClientAccess.evernightMinecraft.player.crit(target);
                };
                executor.execute(criticalEffect);
            }
        }
    }

    protected void performCritical() {
        float[] rotations = new float[2];
        LocalPlayer yawSource = ClientAccess.evernightMinecraft.player;
        rotations[0] = yawSource.getYRot();
        LocalPlayer pitchSource = ClientAccess.evernightMinecraft.player;
        rotations[1] = pitchSource.getXRot();
        if (auraModule.getTarget() != null) {
            if (auraModule.isToggled()) {
                rotations = RotationManager.getRotationsToEntity(auraModule.getTarget());
            }
        }
        switch ((this.modeSetting.getValue())) {
            case NCP:
                LocalPlayer ncpPlayer = ClientAccess.evernightMinecraft.player;
                if (PlayerStateUtils.isInCobweb(ncpPlayer)) {
                    double[] offsets = new double[3];
                    offsets[0] = 1.1e-7F;
                    offsets[1] = 1.1e-8F;
                    offsets[2] = 0.0D;
                    this.sendPositionOffsets(offsets);
                } else {
                    double[] offsets = new double[2];
                    offsets[0] = 1.1e-7F;
                    offsets[1] = 1.1e-8F;
                    this.sendPositionOffsets(offsets);
                }
                break;
            case GRIM:
                LocalPlayer grimXSource = ClientAccess.evernightMinecraft.player;
                double grimX = grimXSource.getX();
                LocalPlayer grimYSource = ClientAccess.evernightMinecraft.player;
                double grimPlayerY = grimYSource.getY();
                double grimY = grimPlayerY - 1e-06D;
                LocalPlayer grimZSource = ClientAccess.evernightMinecraft.player;
                double grimZ = grimZSource.getZ();
                float grimYaw = rotations[0];
                float grimPitch = rotations[1];
                boolean grimOnGround = false;
                PacketSender.sendPositionRotation(grimX, grimY, grimZ, grimYaw, grimPitch, grimOnGround);
                break;
            case GRIMV3:
                LocalPlayer firstXSource = ClientAccess.evernightMinecraft.player;
                double firstX = firstXSource.getX();
                LocalPlayer firstYSource = ClientAccess.evernightMinecraft.player;
                double firstPlayerY = firstYSource.getY();
                double firstY = firstPlayerY + 0.0626D;
                LocalPlayer firstZSource = ClientAccess.evernightMinecraft.player;
                double firstZ = firstZSource.getZ();
                float firstYaw = rotations[0];
                float firstPitch = rotations[1] + AntiCheatModule.ROTATION_JITTER_STEP;
                float firstClampedPitch = this.clampPitch(firstPitch);
                boolean firstOnGround = false;
                PacketSender.sendPositionRotation(firstX, firstY, firstZ, firstYaw, firstClampedPitch, firstOnGround);
                LocalPlayer secondXSource = ClientAccess.evernightMinecraft.player;
                double secondX = secondXSource.getX();
                LocalPlayer secondYSource = ClientAccess.evernightMinecraft.player;
                double secondPlayerY = secondYSource.getY();
                double secondY = secondPlayerY + 0.0455D;
                LocalPlayer secondZSource = ClientAccess.evernightMinecraft.player;
                double secondZ = secondZSource.getZ();
                float secondYaw = rotations[0];
                float secondPitch = rotations[1] - AntiCheatModule.ROTATION_JITTER_STEP;
                float secondClampedPitch = this.clampPitch(secondPitch);
                boolean secondOnGround = false;
                PacketSender.sendPositionRotation(secondX, secondY, secondZ, secondYaw, secondClampedPitch, secondOnGround);
                this.pendingPositionCorrection = true;
                break;
            case PACKET:
                double[] offsets = new double[2];
                offsets[0] = 0.0625101D;
                offsets[1] = 0.0;
                this.sendPositionOffsets(offsets);
        }
    }

    private void sendPositionOffsets(double... offsets) {
        for (double offset : offsets) {
            double x = ClientAccess.evernightMinecraft.player.getX();
            double y = ClientAccess.evernightMinecraft.player.getY() + offset;
            double z = ClientAccess.evernightMinecraft.player.getZ();
            PacketSender.sendPosition(x, y, z, false);
        }
    }

    private boolean shouldApplyCritical(ServerboundInteractPacket packet) {
        if (InteractPacketUtils.getAction(packet) == InteractPacketUtils.Action.ATTACK) {
            if (this.canPerformCritical()) {
                Entity target = InteractPacketUtils.getEntity(packet);
                if (auraModule.isToggled() && auraModule.criticalAttackFlag) {
                    if (this.modeSetting.getValue() != CriticalsModule.ModeChoice.GRIM) {
                        return false;
                    }
                }
                if (target != null) {
                    return target.isAlive() && !(target instanceof EndCrystal);
                }
                return false;
            }
        }
        return false;
    }

    public enum ModeChoice implements NamedEntry {
        PACKET("Packet"),
        NCP("NCP"),
        GRIM("Grim"),
        GRIMV3("2b2t");
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