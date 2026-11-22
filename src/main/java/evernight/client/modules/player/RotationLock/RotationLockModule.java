package evernight.client.modules.player.RotationLock;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.events.core.EventHandler;
import evernight.client.events.player.RotationRequestEvent;
import evernight.client.events.render.WorldRenderEvent;
import evernight.client.managers.player.RotationManager;
import evernight.client.managers.registry.SocialRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.render.FreeLook.FreeLookModule;
import evernight.client.render.util.RenderUtils;
import evernight.client.utils.combat.RaycastUtils;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.math.MathConstants;
import evernight.client.utils.math.MathUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class RotationLockModule extends Module {
    static {
        FreeLookModule checkedFreeLook = ClientContext.getModule(FreeLookModule.class);
    }

    public final Setting<Boolean> yawSetting;
    public final Setting<Boolean> customYawSetting;
    public final Setting<Float> yawValueSetting;
    public final Setting<Boolean> pitchSetting;
    public final Setting<Float> pitchValueSetting;
    private final Setting<ModeChoice> modeSetting;
    private final Setting<Boolean> silentSetting;
    private final Setting<Float> rangeSetting;
    private final Setting<Float> disableRangeSetting;
    private final RotationLockModule.PlainRotationProvider plainRotationProvider;
    private final RotationLockModule.TargetRotationProvider targetRotationProvider;
    private String targetName;

    public RotationLockModule() {
        super("RotationLock", ModuleCategory.PLAYER);
        this.modeSetting = this.add(new EnumSetting<>("Mode", RotationLockModule.ModeChoice.PLAIN));
        this.silentSetting = this.add(new BooleanSetting("Silent", false));
        this.yawSetting = this.add(new BooleanSetting("Yaw", true).markVisibilityDependency());
        this.customYawSetting = this.add(new BooleanSetting("Custom", false).dependsOn(this.yawSetting));
        this.yawValueSetting = this.add(new NumberSetting<>("Value", 0.0F, -180.0F, 180.0F).withUnit("°")
                .dependsOn(this.yawSetting, this.customYawSetting));
        this.pitchSetting = this.add(new BooleanSetting("Pitch", false).markVisibilityDependency());
        this.pitchValueSetting = this.add(new NumberSetting<>("Value", 0.0F, -90.0F, 90.0F).withUnit("°").dependsOn(this.pitchSetting));
        this.rangeSetting = this.add(new NumberSetting<>("Range", 8.0F, 4.0F, 64.0F).withUnit("m"));
        this.disableRangeSetting = this.add(new NumberSetting<>("DisableRange", 0.0F, 0.0F, 32.0F).withUnit("m"));
        this.plainRotationProvider = new RotationLockModule.PlainRotationProvider();
        this.targetRotationProvider = new RotationLockModule.TargetRotationProvider();
        this.modeSetting.setChangeCallback(this::selectTarget);
        Predicate<Object> plainMode = ignored -> {
            Setting<ModeChoice> selectedMode = this.modeSetting;
            return selectedMode.getValue() == RotationLockModule.ModeChoice.PLAIN;
        };
        this.yawSetting.addVisibilityPredicate(plainMode);
        this.customYawSetting.addVisibilityPredicate(plainMode);
        this.yawValueSetting.addVisibilityPredicate(plainMode);
        this.pitchSetting.addVisibilityPredicate(plainMode);
        this.pitchValueSetting.addVisibilityPredicate(plainMode);
        this.rangeSetting.addVisibilityPredicate(ignored -> {
            Setting<ModeChoice> selectedMode = this.modeSetting;
            return selectedMode.getValue() == RotationLockModule.ModeChoice.TARGET;
        });
        Setting<Float> minimumLabelSetting = this.disableRangeSetting;
        SettingLabelCondition minimumEndpoint = SettingLabelCondition.MIN;
        minimumLabelSetting.setBoundaryLabel("None", minimumEndpoint);
        this.yawValueSetting.setConfigName("YawValue");
        this.pitchValueSetting.setConfigName("PitchValue");
    }

    @Override
    public void onEnable() {
        if (this.modeSetting.getValue() != RotationLockModule.ModeChoice.PLAIN) {
            this.selectTarget();
        }
    }

    public float calculateYaw(float currentYaw) {
        Boolean useCustomYaw = this.customYawSetting.getValue();
        if (!useCustomYaw) {
            float quotient = (currentYaw + 1.0F) / MathConstants.FORTY_FIVE_DEGREES;
            return Math.round(quotient) * MathConstants.FORTY_FIVE_DEGREES;
        } else {
            return this.yawValueSetting.getValue();
        }
    }

    private void selectTarget() {
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        Stream<AbstractClientPlayer> candidates = level.players().stream().filter(candidate -> {
            if (ClientAccess.evernightMinecraft.player != candidate) {
                SocialRegistry friends = ClientContext.socialRegistry;
                return !friends.isFriendPlayer(candidate);
            }
            return false;
        }).filter(candidate -> {
            if (!candidate.isDeadOrDying()) {
                return candidate.isAlive();
            }
            return false;
        }).filter(candidate -> {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            Vec3 eyePosition = player.getEyePosition();
            Vec3 candidatePosition = candidate.position();
            double distance = eyePosition.distanceTo(candidatePosition);
            Float maximumDistance = this.rangeSetting.getValue();
            return distance <= maximumDistance.floatValue();
        });
        AbstractClientPlayer target = candidates.min(Comparator.comparingDouble(this::getTargetYawDistance)).orElse(null);
        String selectedName;
        if (target == null) {
            selectedName = null;
        } else {
            selectedName = target.getGameProfile().name();
        }
        this.targetName = selectedName;
    }

    private Vec3 getInterpolatedEyePosition(Entity entity) {
        float partialTick = RenderUtils.getPartialTick();
        Vec3 position = entity.getPosition(partialTick);
        Pose pose = entity.getPose();
        double eyeHeight = entity.getEyeHeight(pose);
        return position.add(0.0, eyeHeight, 0.0);
    }

    @EventHandler
    private void onSilentRotation(RotationRequestEvent event) {
        if (this.silentSetting.getValue()) {
            RotationLockModule.LockedRotation rotation = this.getLockedRotation();
            if (rotation.hasLockedAxis()) {
                ClientContext.rotationManager.forceRotationOverride();
                RotationManager applyManager = ClientContext.rotationManager;
                float[] angles = new float[2];
                angles[0] = rotation.getResolvedYaw();
                angles[1] = rotation.getResolvedPitch();
                applyManager.requestRotation(angles, 1200, true);
            }
        }
    }

    @EventHandler
    private void onPlayerRotation(WorldRenderEvent.Main event) {
        if (this.modeSetting.getValue() == RotationLockModule.ModeChoice.TARGET) {
            if (!this.disableRangeSetting.isAtMinimum()) {
                Vec3 targetPosition = this.findTargetEyePosition();
                if (targetPosition != null) {
                    LocalPlayer player = ClientAccess.evernightMinecraft.player;
                    Vec3 playerEyePosition = player.getEyePosition();
                    double distance = targetPosition.distanceTo(playerEyePosition);
                    Float threshold = this.disableRangeSetting.getValue();
                    if (distance <= threshold.floatValue()) {
                        this.disable();
                        return;
                    }
                }
            }
        }
        Boolean useSilentRotation = this.silentSetting.getValue();
        if (!useSilentRotation) {
            RotationLockModule.LockedRotation rotation = this.getLockedRotation();
            if (rotation.yawLocked()) {
                LocalPlayer yawDestination = ClientAccess.evernightMinecraft.player;
                float yawInterpolation = event.getPartialTick();
                LocalPlayer yawSource = ClientAccess.evernightMinecraft.player;
                float yawPartialTick = RenderUtils.getPartialTick();
                float currentYaw = yawSource.getYRot(yawPartialTick);
                float lockedYaw = rotation.getResolvedYaw();
                float interpolatedYaw = Mth.lerp(yawInterpolation, currentYaw, lockedYaw);
                yawDestination.setYRot(interpolatedYaw);
            }
            if (rotation.pitchLocked()) {
                LocalPlayer pitchDestination = ClientAccess.evernightMinecraft.player;
                float pitchInterpolation = event.getPartialTick();
                LocalPlayer pitchSource = ClientAccess.evernightMinecraft.player;
                float pitchPartialTick = RenderUtils.getPartialTick();
                float currentPitch = pitchSource.getXRot(pitchPartialTick);
                float lockedPitch = rotation.getResolvedPitch();
                float interpolatedPitch = Mth.lerp(pitchInterpolation, currentPitch, lockedPitch);
                pitchDestination.setXRot(interpolatedPitch);
            }
        }
    }

    private float getTargetYawDistance(Entity target) {
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        float playerYaw = player.getYRot();
        float targetYaw = RotationManager.getRotationsToEntity(target)[0];
        return Mth.degreesDifferenceAbs(playerYaw, targetYaw);
    }

    private RotationLockModule.LockedRotation getLockedRotation() {
        RotationLockModule.ModeChoice choice = this.modeSetting.getValue();
        return switch (choice) {
            case PLAIN -> this.plainRotationProvider.getRotation();
            case TARGET -> this.targetRotationProvider.getRotation();
        };
    }

    private Vec3 findTargetEyePosition() {
        if (this.targetName == null) {
            return null;
        }
        ClientLevel level = ClientAccess.evernightMinecraft.level;
        Optional<Vec3> targetPosition = level.players().stream().filter(candidate -> {
            return candidate.getGameProfile().name().equals(this.targetName);
        }).findFirst().map(this::getInterpolatedEyePosition);
        return targetPosition.orElse(null);
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        TARGET("Target");
        private final String displayName;

        ModeChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return this.displayName;
        }
    }

    record LockedRotation(float yaw, float pitch, boolean yawLocked, boolean pitchLocked) {
        public LockedRotation(float yaw, float pitch) {
            this(yaw, pitch, true, true);
        }

        public LockedRotation() {
            this(0.0F, 0.0F, false, false);
        }

        public float getResolvedYaw() {
            if (!this.yawLocked) {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                return player.getYRot();
            }
            return this.yaw;
        }

        public float getResolvedPitch() {
            if (!this.pitchLocked) {
                LocalPlayer player = ClientAccess.evernightMinecraft.player;
                return player.getXRot();
            }
            return this.pitch;
        }

        public boolean hasLockedAxis() {
            return this.yawLocked || this.pitchLocked;
        }
    }

    class TargetRotationProvider {
        public RotationLockModule.LockedRotation getRotation() {
            Vec3 targetPosition = RotationLockModule.this.findTargetEyePosition();
            if (targetPosition == null) {
                return new RotationLockModule.LockedRotation();
            }
            RotationLockModule eyePositionOwner = RotationLockModule.this;
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            Vec3 playerPosition = eyePositionOwner.getInterpolatedEyePosition(player);
            float[] angles = RotationManager.calculateRotations(playerPosition, targetPosition);
            if (PlayerStateUtils.isFallFlying()) {
                return this.avoidObstacles(angles);
            }
            return new RotationLockModule.LockedRotation(angles[0], angles[1]);
        }

        private RotationLockModule.LockedRotation avoidObstacles(float[] angles) {
            float movementYaw = this.getMovementYaw();
            Optional<Float> clearPitch = this.findClearPitch(angles[0], angles[1]);
            if (clearPitch.isPresent()) {
                return new RotationLockModule.LockedRotation(angles[0], clearPitch.get());
            }
            return new RotationLockModule.LockedRotation(this.findClearYaw(movementYaw, angles[0], angles[1]), angles[1]);
        }

        private float getMovementYaw() {
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            Vec3 motion = MathUtils.getPositionDelta(player);
            return motion.horizontalDistance() < 0.05D
                    ? player.getYRot() : (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
        }

        private Optional<Float> findClearPitch(float yaw, float pitch) {
            if (this.isDirectionClear(yaw, pitch)) {
                return Optional.of(pitch);
            }
            for (float offset = 15.0F; offset <= 75.0F; offset += 15.0F) {
                float candidate = Math.min(pitch + offset, 90.0F);
                if (this.isDirectionClear(yaw, candidate)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }

        private float findClearYaw(float movementYaw, float yaw, float pitch) {
            float difference = Mth.clamp(Mth.wrapDegrees(yaw - movementYaw), -50.0F, 50.0F);
            float candidate = Mth.wrapDegrees(movementYaw + difference);
            if (this.isDirectionClear(candidate, pitch)) {
                return candidate;
            }
            for (float offset = 5.0F; offset <= 50.0F; offset += 5.0F) {
                if (difference + offset <= 50.0F) {
                    candidate = Mth.wrapDegrees(movementYaw + difference + offset);
                    if (this.isDirectionClear(candidate, pitch)) {
                        return candidate;
                    }
                }
                if (difference - offset >= -50.0F) {
                    candidate = Mth.wrapDegrees(movementYaw - difference - offset);
                    if (this.isDirectionClear(candidate, pitch)) {
                        return candidate;
                    }
                }
            }
            return movementYaw;
        }

        private boolean isDirectionClear(float yaw, float pitch) {
            Vec3 eyes = ClientAccess.evernightMinecraft.player.getEyePosition();
            Vec3 destination = eyes.add(Vec3.directionFromRotation(pitch, yaw).scale(6.0D));
            return RaycastUtils.isLineVisible(eyes, destination);
        }
    }

    class PlainRotationProvider {
        public RotationLockModule.LockedRotation getRotation() {
            return new RotationLockModule.LockedRotation(this.getYawForLock(),
                    RotationLockModule.this.pitchValueSetting.getValue(),
                    RotationLockModule.this.yawSetting.getValue(), RotationLockModule.this.pitchSetting.getValue());
        }

        private float getYawForLock() {
            RotationLockModule yawOwner = RotationLockModule.this;
            LocalPlayer player = ClientAccess.evernightMinecraft.player;
            float currentYaw = player.getYRot();
            return yawOwner.calculateYaw(currentYaw);
        }
    }
}