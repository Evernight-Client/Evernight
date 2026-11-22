package evernight.client.modules.movement.FastClimb;

import evernight.client.ClientAccess;
import evernight.client.events.core.EventHandler;
import evernight.client.events.core.EventStage;
import evernight.client.events.player.LocalPlayerSendPositionEvent;
import evernight.client.managers.registry.ModuleRegistry;
import evernight.client.managers.player.RotationManager;
import evernight.client.modules.Module;
import evernight.client.modules.ModuleCategory;
import evernight.client.modules.client.AntiCheat.AntiCheatModule;
import evernight.client.settings.types.BooleanSetting;
import evernight.client.settings.types.NumberSetting;
import evernight.client.settings.core.Setting;
import evernight.client.settings.core.SettingLabelCondition;
import evernight.client.utils.network.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;

public class FastClimbModule extends Module {
    private static AntiCheatModule antiCheatModule;
    public final Setting<Float> speedSetting;
    public final Setting<Boolean> airborneSetting;
    private boolean climbableWhenGrounded;

    public FastClimbModule() {
        super("FastClimb", ModuleCategory.MOVEMENT);
        this.speedSetting = this.add(new NumberSetting<>("Speed", 0.2F, 0.0F, 1.0F));
        this.airborneSetting = this.add(new BooleanSetting("Airborne", true));
        this.speedSetting.setBoundaryLabel("Ignore", SettingLabelCondition.MIN);
    }

    public static void bindModules(ModuleRegistry modules) {
        antiCheatModule = modules.getModule(AntiCheatModule.class);
    }

    public boolean canFastClimb() {
        if (!this.isToggled()) {
            return false;
        }
        return this.airborneSetting.getValue() || this.climbableWhenGrounded;
    }

    private boolean isClimbableAtPlayer(BlockPos position) {
        return ClientAccess.evernightMinecraft.level.getBlockState(position).is(BlockTags.CLIMBABLE)
                && BlockPos.containing(ClientAccess.evernightMinecraft.player.position()).equals(position);
    }

    private void updateGroundedClimbable() {
        if (!ClientAccess.evernightMinecraft.player.onGround()) {
            return;
        }
        boolean foundClimbable = false;
        AABB bounds = ClientAccess.evernightMinecraft.player.getBoundingBox().inflate(0.1D);
        for (BlockPos position : BlockPos.betweenClosed(bounds)) {
            if (this.isClimbableAtPlayer(position)) {
                foundClimbable = true;
                break;
            }
        }
        this.climbableWhenGrounded = foundClimbable;
    }

    @EventHandler
    private void onSendPosition(LocalPlayerSendPositionEvent event) {
        if (EventStage.Pre == event.getStage()) {
            this.updateGroundedClimbable();
        }
        if (event.getStage() == EventStage.Post) {
            return;
        }
        if (!RotationManager.isMovementSyncEnabled()) {
            return;
        }
        if (!this.canFastClimb()) {
            return;
        }
        AABB scanBounds = ClientAccess.evernightMinecraft.player.getBoundingBox().inflate(0.1D);
        BlockPos.betweenClosedStream(scanBounds).map(BlockPos::immutable).filter(this::isClimbableAtPlayer)
                .forEach(position -> {
                    if (!antiCheatModule.is2b2tEnabled()) {
                        PacketSender.sendPlayerAction(Action.ABORT_DESTROY_BLOCK, position, Direction.UP);
                    }
                    PacketSender.sendPlayerAction(Action.STOP_DESTROY_BLOCK, position, Direction.UP);
                });
    }
}