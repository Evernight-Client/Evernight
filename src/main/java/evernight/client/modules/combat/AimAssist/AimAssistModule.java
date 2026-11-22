package evernight.client.modules.combat.AimAssist;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.render.util.RenderUtils;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.utils.inventory.ItemUtils;
import evernight.client.utils.math.MathConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.stream.Stream;

public class AimAssistModule extends Module {
    private final Setting<Float> rangeSetting;
    private final Setting<Float> speedSetting;
    private final Setting<Float> fovSetting;
    private final Setting<Boolean> onlyWeaponSetting;
    private final Setting<Boolean> ignoreInvisibleSetting;
    private final Setting<Boolean> lockTargetSetting;
    private final Setting<Boolean> friendsSetting;
    private final Setting<Boolean> instantSetting;
    private AbstractClientPlayer targetPlayer;

    public AimAssistModule() {
        super("AimAssist", ModuleCategory.COMBAT);
        this.rangeSetting = this.add(new NumberSetting<>("Range", 4.0F, 1.0F, 6.0F).withUnit("m"));
        this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 0.1F, 15.0F));
        this.fovSetting = this.add(new NumberSetting<>("Fov", 90.0F, 1.0F, 360.0F));
        this.onlyWeaponSetting = this.add(new BooleanSetting("OnlyWeapon", true));
        this.ignoreInvisibleSetting = this.add(new BooleanSetting("IgnoreInvisible", true));
        this.lockTargetSetting = this.add(new BooleanSetting("LockTarget", true));
        this.friendsSetting = this.add(new BooleanSetting("Friends", true));
        this.instantSetting = this.add(new BooleanSetting("Instant", false));
    }

    private static double yawDifference(Entity entity) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        float partialTick = RenderUtils.getPartialTick();
        float viewYaw = player.getViewYRot(partialTick);
        double difference = (viewYaw - yawToEntity(entity)) % MathConstants.FULL_TURN_DEGREES;
        double wrapped = (difference + 540.0D) % MathConstants.FULL_TURN_DEGREES;
        return wrapped - 180.0D;
    }

    private static boolean isWithinFov(Entity entity, double fieldOfView) {
        fieldOfView = (float) (fieldOfView * MathConstants.ONE_HALF);
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        float partialTick = RenderUtils.getPartialTick();
        float viewYaw = player.getViewYRot(partialTick);
        double difference = (viewYaw - yawToEntity(entity)) % MathConstants.FULL_TURN_DEGREES;
        double wrapped = (difference + 540.0D) % MathConstants.FULL_TURN_DEGREES;
        double yawDifference = wrapped - 180.0D;
        return yawDifference > 0.0 && yawDifference < fieldOfView || -fieldOfView < yawDifference && yawDifference < 0.0;
    }

    private static float yawToEntity(Entity entity) {
        double targetX = entity.getX();
        LocalPlayer xPlayer = ClientAccess.evernightMinecraft.player;
        double deltaX = targetX - xPlayer.getX();
        double targetZ = entity.getZ();
        LocalPlayer zPlayer = ClientAccess.evernightMinecraft.player;
        double deltaZ = targetZ - zPlayer.getZ();
        double angle = Math.atan2(deltaX, deltaZ);
        double degrees = angle * 57.2957795D;
        return (float) (degrees * -1.0D);
    }

    @EventHandler
    private void onWorldRender(WorldRenderEvent.Main event) {
        if (ClientAccess.evernightMinecraft.screen != null) {
            return;
        }
        if (!(ClientAccess.evernightMinecraft.player.getMainHandItem().getItem() instanceof AxeItem)) {
            if (!ItemUtils.isSword(ClientAccess.evernightMinecraft.player.getMainHandItem())) {
                if (this.onlyWeaponSetting.getValue()) {
                    return;
                }
            }
        }
        boolean keepTarget = false;
        if (this.targetPlayer != null) {
            keepTarget = this.lockTargetSetting.getValue();
        }
        if (!keepTarget) {
            Stream<AbstractClientPlayer> players = ClientAccess.evernightMinecraft.level.players().stream();
            Comparator<AbstractClientPlayer> byDistance = Comparator.comparing(candidate -> {
                return ClientAccess.evernightMinecraft.player.distanceTo(candidate);
            });
            this.targetPlayer = players.sorted(byDistance).filter(this::isValidTarget).findFirst().orElse(null);
        }
        AbstractClientPlayer checkedTarget = this.targetPlayer;
        if (!this.isValidTarget(checkedTarget)) {
            this.targetPlayer = null;
            return;
        }
        if (ClientAccess.evernightMinecraft.crosshairPickEntity == this.targetPlayer) {
            return;
        }
        if (this.instantSetting.getValue()) {
            AbstractClientPlayer aimedTarget = this.targetPlayer;
            AABB bounds = aimedTarget.getBoundingBox();
            Vec3 center = bounds.getCenter();
            float[] rotations = RotationManager.getRotationsToPosition(center);
            float yaw = rotations[0];
            LocalPlayer yawPlayer = ClientAccess.evernightMinecraft.player;
            yawPlayer.setYRot(yaw);
            float pitch = rotations[1];
            ClientAccess.evernightMinecraft.player.setXRot(pitch);
        } else {
            AbstractClientPlayer aimedTarget = this.targetPlayer;
            double yawDifference = yawDifference(aimedTarget);
            if (!(yawDifference > 1.0D) && !(yawDifference < -1.0D)) {
                return;
            }
            Float speedValue = this.speedSetting.getValue();
            double scaledDifference = yawDifference * speedValue.floatValue();
            float yawStep = (float) (scaledDifference * 0.01F);
            LocalPlayer yawPlayer = ClientAccess.evernightMinecraft.player;
            LocalPlayer viewPlayer = ClientAccess.evernightMinecraft.player;
            float partialTick = RenderUtils.getPartialTick();
            float nextYaw = viewPlayer.getViewYRot(partialTick) - yawStep;
            yawPlayer.setYRot(nextYaw);
        }
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof Player) || entity == ClientAccess.evernightMinecraft.player) {
            return false;
        }
        if (!entity.isAlive()) {
            return false;
        }
        if (this.ignoreInvisibleSetting.getValue() && entity.isInvisible()) {
            return false;
        }
        double fieldOfView = (this.fovSetting.getValue()).floatValue();
        if (!isWithinFov(entity, fieldOfView)) {
            return false;
        }
        float distance = ClientAccess.evernightMinecraft.player.distanceTo(entity);
        Float rangeValue = this.rangeSetting.getValue();
        if (!(distance <= rangeValue)) {
            return false;
        }
        if (this.friendsSetting.getValue()) {
            SocialRegistry socialRegistry = ClientContext.socialRegistry;
            String name = ((Player) entity).getGameProfile().name();
            if (socialRegistry.isFriendName(name)) {
                return false;
            }
        }
        return hasLineOfSight(entity);
    }

    private boolean hasLineOfSight(Entity entity) {
        Vec3 start = new Vec3(ClientAccess.evernightMinecraft.player.getX(),
                ClientAccess.evernightMinecraft.player.getY() + ClientAccess.evernightMinecraft.player.getEyeHeight(),
                ClientAccess.evernightMinecraft.player.getZ());
        Vec3 feet = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        ClientLevel feetWorld = ClientAccess.evernightMinecraft.level;
        ClipContext feetRay = new ClipContext(start, feet, Block.COLLIDER, Fluid.NONE, ClientAccess.evernightMinecraft.player);
        BlockHitResult feetHit = feetWorld.clip(feetRay);
        if (feetHit.getType() == Type.MISS) {
            return true;
        }
        Vec3 eyes = new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
        ClientLevel eyesWorld = ClientAccess.evernightMinecraft.level;
        ClipContext eyesRay = new ClipContext(start, eyes, Block.COLLIDER, Fluid.NONE, ClientAccess.evernightMinecraft.player);
        BlockHitResult eyesHit = eyesWorld.clip(eyesRay);
        return eyesHit.getType() == Type.MISS;
    }
}