package evernight.client.modules.movement.FastWeb;

import evernight.client.ClientAccess;
import evernight.client.ClientContext;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.utils.entity.PlayerStateUtils;
import evernight.client.utils.movement.MovementPhysics;
import evernight.client.utils.network.PacketSender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerAiStepEvent;
import evernight.client.events.player.LocalPlayerMoveEvent;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.settings.core.NamedEntry;
import evernight.client.settings.core.Setting;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.EnumSetting;
import evernight.client.settings.types.NumberSetting;

public class FastWebModule extends Module {
    private static AntiCheatModule antiCheatModule;
    final Setting<ModeChoice> modeSetting;
    private final Setting<Boolean> horizontalSetting;
    private final Setting<Float> speedSetting;
    private final Setting<Boolean> verticalSetting;
    private final Setting<Float> vSpeedSetting;
    private final Setting<Boolean> upwardsSetting;
    private boolean usingStrictTimer;

    public FastWebModule() {
        super("FastWeb", ModuleCategory.MOVEMENT);
        this.modeSetting = this.add(new EnumSetting<>("Mode", FastWebModule.ModeChoice.PLAIN));
        Predicate<Object> showMovement = ignored -> this.modeSetting.getValue() != FastWebModule.ModeChoice.GRIM;
        this.horizontalSetting = this.add(new BooleanSetting("Horizontal", false, showMovement).markVisibilityDependency());
        this.speedSetting = this.add(new NumberSetting<>("Speed", 1.0F, 1.0F, 2.0F, showMovement).dependsOn(this.horizontalSetting));
        this.verticalSetting = this.add(new BooleanSetting("Vertical", false, showMovement).markVisibilityDependency());
        this.vSpeedSetting = this.add(new NumberSetting<>("VSpeed", 1.0F, 1.0F, 2.0F, showMovement).dependsOn(this.verticalSetting));
        Predicate<Object> showUpwards = ignored -> this.modeSetting.getValue() == FastWebModule.ModeChoice.PLAIN;
        this.upwardsSetting = this.add(new BooleanSetting("Upwards", false, showUpwards).dependsOn(this.verticalSetting));
        this.usingStrictTimer = false;
        this.setDrawn(false);
    }

    private static double currentXDisplacement() {
        return ClientAccess.evernightMinecraft.player.getX() - ClientAccess.evernightMinecraft.player.xo;
    }

    public static void bindModules(ModuleRegistry registry) {
        antiCheatModule = registry.getModule(AntiCheatModule.class);
    }

    public void reset() {
        ClientContext.playerTickController.releaseMultiplier(this);
        this.usingStrictTimer = false;
    }

    public boolean isUsingStrictTimer() {
        return this.usingStrictTimer;
    }

    private boolean isStrictMode() {
        return this.modeSetting.getValue() == FastWebModule.ModeChoice.STRICT;
    }

    public boolean isGrimEnabled() {
        if (!this.isToggled()) {
            return false;
        }
        return this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM;
    }

    @EventHandler(priority = -100)
    private void onAiStep(LocalPlayerAiStepEvent event) {
        if (!this.isGrimEnabled()) {
            return;
        }
        if (!ClientAccess.evernightMinecraft.player.input.keyPresses.jump()) {
            return;
        }
        if (PlayerStateUtils.isInCobweb(ClientAccess.evernightMinecraft.player)) {
            ClientAccess.evernightMinecraft.player.setSprinting(false);
        }
    }

    @EventHandler
    private void onMove(LocalPlayerMoveEvent event) {
        if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
            this.reset();
            return;
        }
        if (!PlayerStateUtils.isInCobweb(ClientAccess.evernightMinecraft.player)) {
            this.reset();
            return;
        }
        if (this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM) {
            this.reset();
            return;
        }
        float horizontalMultiplier = this.speedSetting.getValue();
        if (this.verticalSetting.getValue()) {
            horizontalMultiplier = this.applyVerticalDescent(event, horizontalMultiplier);
            if (this.upwardsSetting.getValue()) {
                Input jumpKeys = ClientAccess.evernightMinecraft.player.input.keyPresses;
                if (jumpKeys.jump()) {
                    if (!this.isStrictMode()) {
                        LocalPlayer ascentPlayer = ClientAccess.evernightMinecraft.player;
                        Vec3 ascentVelocity = ascentPlayer.getDeltaMovement();
                        Axis ascentAxis = Axis.Y;
                        double upwardVelocity = this.vSpeedSetting.getValue();
                        Vec3 updatedAscentVelocity = ascentVelocity.with(ascentAxis, upwardVelocity);
                        ascentPlayer.setDeltaMovement(updatedAscentVelocity);
                        double upwardEventY = this.vSpeedSetting.getValue();
                        event.setY(upwardEventY);
                    }
                }
            }
        }
        if (MovementPhysics.isLocalPlayerMoving()) {
            if (this.horizontalSetting.getValue()) {
                boolean includeEffects = true;
                double horizontalSpeed = MovementPhysics.getBaseMoveSpeed(includeEffects)
                        * horizontalMultiplier;
                LocalPlayerMoveEvent horizontalEvent = event;
                double[] horizontalMotion = MovementPhysics.applyHorizontalMotion(horizontalEvent, horizontalSpeed
                );
                LocalPlayer horizontalReceiverSnapshot = ClientAccess.evernightMinecraft.player;
                double horizontalXSnapshot = horizontalMotion[0];
                LocalPlayer yVelocityPlayer = ClientAccess.evernightMinecraft.player;
                double horizontalZ = horizontalMotion[1];
                double preservedY = yVelocityPlayer.getDeltaMovement().y;
                horizontalReceiverSnapshot.setDeltaMovement(horizontalXSnapshot, preservedY, horizontalZ);
            }
        }
    }

    private float applyVerticalDescent(LocalPlayerMoveEvent event, float horizontalMultiplier) {
        if (!ClientAccess.evernightMinecraft.player.input.keyPresses.shift() || ClientAccess.evernightMinecraft.player.onGround()) {
            this.reset();
            return horizontalMultiplier;
        }
        if (this.isStrictMode()) {
            this.usingStrictTimer = true;
            ClientContext.playerTickController.requestMultiplier(this, this.vSpeedSetting.getValue() * 6.0F);
            return horizontalMultiplier;
        }
        this.reset();
        LocalPlayer player = ClientAccess.evernightMinecraft.player;
        player.setDeltaMovement(player.getDeltaMovement().with(Axis.Y, -this.vSpeedSetting.getValue()));
        event.setY(-this.vSpeedSetting.getValue());
        return Math.min(horizontalMultiplier, 1.2F);
    }

    @EventHandler
    private void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (EventStage.Pre == event.getStage()) {
            Vec3 displacement = new Vec3(currentXDisplacement(),
                    ClientAccess.evernightMinecraft.player.getY() - ClientAccess.evernightMinecraft.player.yo,
                    ClientAccess.evernightMinecraft.player.getZ() - ClientAccess.evernightMinecraft.player.zo);
            if (this.isGrimEnabled()) {
                if (displacement.lengthSqr() > 0.0) {
                    AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox();
                    BlockPos.betweenClosedStream(bounds).map(BlockPos::immutable)
                            .filter(webPos -> ClientAccess.evernightMinecraft.level.getBlockState(webPos).is(Blocks.COBWEB))
                            .forEach(webPos -> {
                                if (!antiCheatModule.is2b2tEnabled()) {
                                    PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, webPos, Direction.UP);
                                }
                                PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, webPos, Direction.UP);
                            });
                }
            }
            if (this.modeSetting.getValue() == FastWebModule.ModeChoice.GRIM) {
                if (ClientAccess.evernightMinecraft.player.input.keyPresses.shift()) {
                    if (!ClientAccess.evernightMinecraft.player.onGround()) {
                        if (PlayerStateUtils.isInCobweb(ClientAccess.evernightMinecraft.player)) {
                            double playerY = ClientAccess.evernightMinecraft.player.getY();
                            double randomizedY = playerY + Math.random();
                            double spoofedY = randomizedY + 1.0D;
                            event.setY(spoofedY);
                        }
                    }
                }
            }
        }
    }

    public enum ModeChoice implements NamedEntry {
        PLAIN("Plain"),
        STRICT("Strict"),
        LAGBACK("LagBack"),
        GRIM("Grim");
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